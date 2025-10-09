package com.civlabs.radios.voice;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.model.Radio;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Distance profile (clean → delayed → noisy):
 *  - <= 80 blocks: clean (no delay, no distortion)
 *  - Delay grows linearly beyond 80 at 0.007 s per block (no jitter)
 *  - Optional late-onset drops start ~900 (kept mild)
 *  - STATIC uses SNR-based mixing (distance → target SNR), disabled when near
 *
 * No random per-packet jitter is used (to avoid frame reordering).
 */
public class VoiceBridge {

    private final CivLabsRadiosPlugin plugin;
    private VoicechatServerApi api;

    // optional: isolate operators into TX groups
    private final Map<Integer, UUID> txGroupIds = new HashMap<>();
    // RX radio -> locational speaker
    private final Map<UUID, LocationalSpeaker> speakers = new HashMap<>();

    // --- Opus transcode for interference (if available) ---
    private de.maxhenkel.voicechat.api.opus.OpusDecoder decoder;
    private de.maxhenkel.voicechat.api.opus.OpusEncoder encoder;
    private static final int SVC_SAMPLE_RATE = 48000;
    private Interference interference;

    // Noise starts only after this distance
    private static final double NOISE_START_DIST = 400.0; // completely off at/under 400

    public VoiceBridge(CivLabsRadiosPlugin plugin) {
        this.plugin = plugin;
    }

    /* =========================
       SERVER/VOICE LIFECYCLE
       ========================= */
    public void onServerStarted(VoicechatServerStartedEvent event) {
        this.api = event.getVoicechat();

        // Try to create encoder/decoder for PCM processing (for interference/static)
        try {
            decoder = api.createDecoder();
            encoder = api.createEncoder();
            interference = new Interference(SVC_SAMPLE_RATE);
            logDebug("Opus encoder/decoder initialized for interference.");
        } catch (Throwable t) {
            decoder = null;
            encoder = null;
            interference = null;
            plugin.getLogger().warning("[VoiceBridge] Could not init Opus codec for interference: " + t.getMessage());
        }

        // Build per-frequency TX groups (optional isolation)
        final int max = plugin.getConfig().getInt("maxFrequencies", 10);
        final boolean hidden = plugin.getConfig().getBoolean("voice.txGroupHidden", true);
        final boolean persistent = plugin.getConfig().getBoolean("voice.txGroupPersistent", true);
        final String prefix = plugin.getConfig().getString("voice.txGroupPrefix", "TX ");

        txGroupIds.clear();
        for (int f = 1; f <= max; f++) {
            Group g = api.groupBuilder()
                    .setName(prefix + f)
                    .setHidden(hidden)
                    .setPersistent(persistent)
                    .setType(Group.Type.ISOLATED)
                    .build();
            txGroupIds.put(f, g.getId());
        }
        logDebug("Voice started: created " + txGroupIds.size() + " TX groups.");
    }

    /* =========================
       AUDIO HANDLING
       ========================= */
    public void onMicPacket(MicrophonePacketEvent event) {
        if (api == null) return;

        UUID talker = event.getSenderConnection().getPlayer().getUuid();
        Optional<Radio> txOpt = plugin.store().byOperator(talker);
        if (txOpt.isEmpty()) return;

        Radio tx = txOpt.get();
        if (!tx.isEnabled() || tx.getTransmitFrequency() < 1) return;

        byte[] opusData = event.getPacket().getOpusEncodedData();
        if (opusData == null || opusData.length == 0) return;

        int txFreq = tx.getTransmitFrequency();
        int maxAudible = plugin.getConfig().getInt("speakerRadius", 30);

        final Location txLoc = tx.getLocation();
        if (txLoc == null || txLoc.getWorld() == null) return;

        // Mute direct SVC so only radio path is heard (version-safe)
        if (plugin.getConfig().getBoolean("voice.mute_direct", true)) {
            try {
                event.cancel(); // newer SVC API
            } catch (Throwable ignored) {
                try {
                    MicrophonePacketEvent.class.getMethod("setPacketMuted", boolean.class).invoke(event, true);
                } catch (Throwable ignored2) { /* ignore */ }
            }
        }

        List<Radio> receivers = plugin.store().listenersOn(txFreq);
        if (receivers == null || receivers.isEmpty()) {
            logDebug("No RX radios tuned to TX " + txFreq + ".");
            return;
        }

        for (Radio rx : receivers) {
            Location rxLoc = rx.getLocation();
            if (rxLoc == null) continue;

            // ensure/refresh speaker
            LocationalSpeaker speaker = speakers.computeIfAbsent(rx.getId(),
                    id -> new LocationalSpeaker(api, rxLoc, maxAudible));
            speaker.ensureAt(rxLoc, maxAudible);

            // distance
            double dist;
            if (rxLoc.getWorld() == null || !rxLoc.getWorld().equals(txLoc.getWorld())) {
                dist = 10_000d; // cross-world = very far
            } else {
                dist = txLoc.distance(rxLoc);
            }

            long delayTicks = delayTicksForDistance(dist);
            double dropChance = dropChanceForDistance(dist);

            // Mild, late-onset drops only (kept low so intelligibility isn't destroyed)
            if (dropChance > 0 && ThreadLocalRandom.current().nextDouble() < dropChance) {
                logDebug("DROP for RX " + rx.getId() + " dist=" + (int) dist + " p=" + String.format(Locale.US, "%.3f", dropChance));
                continue;
            }

            // Build payload: optionally add distance-scaled static with SNR control
            byte[] payload = opusData;
            if (decoder != null && encoder != null && interference != null && dist > NOISE_START_DIST) {
                try {
                    double targetSnrDb = targetSnrForDistance(dist); // larger dB = cleaner
                    short[] pcm = decoder.decode(opusData);
                    interference.mixStaticWithTargetSnr(pcm, targetSnrDb);
                    payload = encoder.encode(pcm);
                } catch (Throwable t) {
                    logDebug("Interference processing failed: " + t.getMessage());
                    payload = opusData;
                }
            }

            // Schedule playback with pure delay (no jitter)
            if (delayTicks <= 0) {
                speaker.playFrame(payload);
            } else {
                final byte[] finalPayload = payload;
                final LocationalSpeaker finalSpeaker = speaker;
                Bukkit.getScheduler().runTaskLater(plugin, () -> finalSpeaker.playFrame(finalPayload), delayTicks);
            }
        }
    }

    /* =========================
       DISTANCE PROFILE (smooth, no jitter)
       ========================= */

    // Clean within this radius
    private static final int CLEAR_RANGE = 80;

    // Delay per block beyond CLEAR_RANGE (seconds)
    // e.g., 600 blocks => (600-80)*0.007 ≈ 3.64 s expected delay
    private static final double DELAY_PER_BLOCK = 0.007;

    // JITTER DISABLED (kept for reference, not used)
    @SuppressWarnings("unused")
    private static final double JITTER_PCT = 0.0;

    // Late-onset drops (probabilities kept mild)
    private static final double D0 = CLEAR_RANGE, P0 = 0.00;
    private static final double D1 = 800.0,       P1 = 0.00;
    private static final double D2 = 900.0,       P2 = 0.01;
    private static final double D3 = 1100.0,      P3 = 0.035;
    private static final double D4 = 1300.0,      P4 = 0.10;
    private static final double D5 = 1600.0,      P5 = 0.15;
    private static final double D6 = 2000.0,      P6 = 0.20;
    private static final double P_MAX = 0.20;

    private long delayTicksForDistance(double d) {
        if (d <= CLEAR_RANGE) return 0;
        double sec = (d - CLEAR_RANGE) * DELAY_PER_BLOCK;
        return Math.max(0, Math.round(sec * 20.0)); // 20 TPS
    }

    private double dropChanceForDistance(double d) {
        double p;
        if      (d <= D0) p = P0;
        else if (d <= D1) p = lerp(d, D0, P0, D1, P1);
        else if (d <= D2) p = lerp(d, D1, P1, D2, P2);
        else if (d <= D3) p = lerp(d, D2, P2, D3, P3);
        else if (d <= D4) p = lerp(d, D3, P3, D4, P4);
        else if (d <= D5) p = lerp(d, D4, P4, D5, P5);
        else if (d <= D6) p = lerp(d, D5, P5, D6, P6);
        else              p = P6;

        if (p < 0) p = 0;
        if (p > P_MAX) p = P_MAX;
        return p;
    }

    private static double lerp(double x, double x0, double y0, double x1, double y1) {
        if (x1 == x0) return y1;
        double t = (x - x0) / (x1 - x0);
        return y0 + t * (y1 - y0);
    }

    /* =========================
       NOISE PROFILE (target SNR vs distance)
       ========================= */

    /**
     * Target speech-to-noise ratio in dB as a function of distance.
     * Higher = cleaner (less noise). Noise is OFF at/under NOISE_START_DIST.
     *
     * Examples with defaults:
     *  400–700 blocks  : ~48 → 36 dB (barely audible hiss)
     *  ~1000 blocks    : ~28 dB (audible but speech dominant)
     *  ~1500 blocks    : ~12 dB (annoying)
     *  1800–2200+      :  8 →  6 dB (very noisy)
     */
    private double targetSnrForDistance(double dist) {
        if (dist <= NOISE_START_DIST) return Double.POSITIVE_INFINITY; // no noise
        // Breakpoints you can tune freely:
        final double A = 700.0;  final double SNR_A = 36.0; // gentle
        final double B = 1000.0; final double SNR_B = 28.0;
        final double C = 1500.0; final double SNR_C = 12.0;
        final double D = 1800.0; final double SNR_D =  8.0;
        final double E = 2200.0; final double SNR_E =  6.0;

        if (dist <= A) {
            // 400..700 : 48 → 36 dB with smooth easing from "no noise" region
            double t = smoothstep01((dist - NOISE_START_DIST) / (A - NOISE_START_DIST));
            return 48.0 + (SNR_A - 48.0) * t;
        } else if (dist <= B) {
            double t = smoothstep01((dist - A) / (B - A));
            return SNR_A + (SNR_B - SNR_A) * t;
        } else if (dist <= C) {
            double t = smoothstep01((dist - B) / (C - B));
            return SNR_B + (SNR_C - SNR_B) * t;
        } else if (dist <= D) {
            double t = smoothstep01((dist - C) / (D - C));
            return SNR_C + (SNR_D - SNR_C) * t;
        } else if (dist <= E) {
            double t = smoothstep01((dist - D) / (E - D));
            return SNR_D + (SNR_E - SNR_D) * t;
        } else {
            return SNR_E;
        }
    }

    // Smoothstep 0..1 easing (gentle S-curve)
    private static double smoothstep01(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    /* =========================
       GROUP/OPERATOR BINDING & SPEAKERS
       ========================= */
    public void bindOperator(Radio r, Player operator) {
        if (api == null) return;
        if (!plugin.getConfig().getBoolean("voice.isolateOperatorInTxGroup", true)) return;

        VoicechatConnection c = api.getConnectionOf(operator.getUniqueId());
        if (c == null) return;
        UUID gid = txGroupIds.get(r.getTransmitFrequency());
        if (gid == null) return;
        Group g = api.getGroup(gid);
        if (g != null) c.setGroup(g);
    }

    public void unbindOperator(UUID operator, int txFreq) {
        if (api == null) return;
        Player p = Bukkit.getPlayer(operator);
        if (p == null) return;
        VoicechatConnection c = api.getConnectionOf(operator);
        if (c != null) c.setGroup(null);
    }

    public void updateSpeakerFor(Radio r) {
        Integer rx = r.getListenFrequency();
        if (rx == null || rx < 1) {
            removeSpeaker(r.getId());
        }
    }

    public void removeSpeaker(UUID radioId) {
        LocationalSpeaker sp = speakers.remove(radioId);
        if (sp != null) sp.close();
    }

    public void shutdownAllSpeakers() {
        for (LocationalSpeaker sp : speakers.values()) sp.close();
        speakers.clear();
    }

    public void tickSpeakers() {
        // no-op
    }

    /* =========================
       DEBUG
       ========================= */
    private void logDebug(String msg) {
        if (plugin.getConfig().getBoolean("debug.voice", false)) {
            plugin.getLogger().info("[VoiceBridge] " + msg);
        }
    }

    /* =========================
       INTERFERENCE (band-limited static, SNR-based)
       ========================= */

    /**
     * Band-limited noise (telephone band ~300–3400 Hz) with SNR-based mixing.
     * We normalize the noise per frame to hit the desired SNR versus the measured
     * speech RMS, so nearby speech always dominates and far speech can be overwhelmed.
     */
    private static final class Interference {
        private static final class OnePole {
            double a0, a1, b1, z1;
            void setHP(double fc, double fs) {
                double x = Math.exp(-2.0 * Math.PI * fc / fs);
                a0 = (1 + x) / 2.0; a1 = -(1 + x) / 2.0; b1 = x;
            }
            void setLP(double fc, double fs) {
                double x = Math.exp(-2.0 * Math.PI * fc / fs);
                a0 = 1 - x; a1 = 0; b1 = x;
            }
            float process(float x) {
                double y = a0 * x + a1 * z1 + b1 * z1;
                z1 = y;
                return (float) y;
            }
        }

        private final OnePole hp = new OnePole();
        private final OnePole lp = new OnePole();
        private final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        private final int sampleRate;

        public Interference(int sampleRate) {
            this.sampleRate = sampleRate;
            hp.setHP(300.0, sampleRate);
            lp.setLP(3400.0, sampleRate);
        }

        /** Mix band-limited noise into PCM to reach a target SNR (dB). */
        public void mixStaticWithTargetSnr(short[] pcm, double targetSnrDb) {
            if (!Double.isFinite(targetSnrDb)) return; // no noise case

            // 1) Measure voice RMS
            double voiceRms = rmsPcm(pcm);
            if (voiceRms < 1e-6) {
                // ultra-quiet: keep a tiny cosmetic hiss, but very low
                targetSnrDb = 48.0;
                voiceRms = 1e-6;
            }

            // Desired noise RMS for target SNR: SNR = 20*log10(voiceRms/noiseRms)
            double desiredNoiseRms = voiceRms / Math.pow(10.0, targetSnrDb / 20.0);
            if (desiredNoiseRms <= 0) return;

            // 2) Generate & band-limit noise buffer; measure its RMS
            float[] nbuf = new float[pcm.length];
            double acc2 = 0.0;
            for (int i = 0; i < nbuf.length; i++) {
                float n = (rnd.nextFloat() * 2f - 1f);
                n = hp.process(n);
                n = lp.process(n);
                nbuf[i] = n;
                acc2 += (double) n * n;
            }
            double baseNoiseRms = Math.sqrt(acc2 / Math.max(1, nbuf.length));
            if (baseNoiseRms < 1e-9) return;

            // 3) Scale noise to the desired RMS and mix in
            double gain = desiredNoiseRms / baseNoiseRms;

            for (int i = 0; i < pcm.length; i++) {
                double x = pcm[i] / 32768.0;
                x += gain * nbuf[i];
                if (x > 1.0) x = 1.0;
                if (x < -1.0) x = -1.0;
                pcm[i] = (short) Math.round(x * 32767.0);
            }
        }

        private static double rmsPcm(short[] pcm) {
            double acc = 0.0;
            for (short s : pcm) {
                double x = s / 32768.0;
                acc += x * x;
            }
            return Math.sqrt(acc / Math.max(1, pcm.length));
        }
    }
}
