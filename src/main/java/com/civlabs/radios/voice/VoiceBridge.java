package com.civlabs.radios.voice;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.model.Radio;
import com.civlabs.radios.util.RadioMath;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles bridging voice packets and applying distance-based delay/static.
 * Also refuses to play if the receiver has no vertical antenna stack.
 */
public class VoiceBridge {

    private final CivLabsRadiosPlugin plugin;
    private VoicechatServerApi api;

    private final Map<Integer, UUID> txGroupIds = new HashMap<>();
    private final Map<UUID, LocationalSpeaker> speakers = new HashMap<>();
    private boolean interferenceEnabled;
    private int sampleRate;
    private double maxNoiseDb;
    private double farDistance;
    private int maxAudible;

    public VoiceBridge(CivLabsRadiosPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void onServerStarted(VoicechatServerStartedEvent event) {
        this.api = event.getVoicechat();

        logDebug("Voice started: created ");
    }
    public void loadConfig(){
        interferenceEnabled = plugin.getConfig().getBoolean("voice.interference.enabled", true);
        sampleRate = plugin.getConfig().getInt("voice.sampleRate", 48000);
        maxNoiseDb = plugin.getConfig().getDouble("voice.interference.maxNoiseDb", -24.0);
        farDistance = plugin.getConfig().getDouble("voice.interference.farDistance", 6000.0);
        maxAudible = plugin.getConfig().getInt("speakerRadius", 30);
    }
    
    public void onMicPacket(MicrophonePacketEvent event) {
        // this function is too big
        if (api == null || event.getSenderConnection() == null) return;

        UUID talker = event.getSenderConnection().getPlayer().getUuid();

        Radio tx = plugin.store().getAll().stream()
                .filter(Radio::isEnabled)
                .filter(r -> r.getTransmitFrequency() > 0)
                .filter(r -> talker.equals(r.getOperator()))
                .findFirst()
                .orElse(null);
        if (tx == null) return;

        byte[] opusData = event.getPacket().getOpusEncodedData();
        if (opusData == null || opusData.length == 0) return;

        int txFreq = tx.getTransmitFrequency();
        Location txLoc = tx.getLocation();
        if (txLoc == null || txLoc.getWorld() == null) return;

        List<Radio> receivers = plugin.store().listenersOn(txFreq);
        if (receivers == null || receivers.isEmpty()) return;

        loadConfig();

        for (Radio rx : receivers) {
            RadioMath.recomputeAntennaAndRange(rx);
            if (rx.getAntennaCount() <= 0) {
                /* this isn't needed cause the operator of the listening radio doesn't matter
                UUID opId = rx.getOperator();
                Player op = (opId != null ? Bukkit.getPlayer(opId) : null);
                if (op != null) op.sendMessage(Component.text("§cRadio cannot find any antennas above it."));
                */
                continue;
            }

            Location rxLoc = rx.getLocation();
            if (rxLoc == null) continue;

            LocationalSpeaker speaker = speakers.computeIfAbsent(rx.getId(),
                    id -> new LocationalSpeaker(api, rxLoc, maxAudible));
            speaker.ensureAt(rxLoc, maxAudible);

            // we should make it so that if it's not in the same world it doesn't work
            double dist = (rxLoc.getWorld() == null || !rxLoc.getWorld().equals(txLoc.getWorld()))
                    ? 10000d
                    : txLoc.distance(rxLoc);

            long delayTicks = delayTicksForDistance(dist);
            double dropChance = dropChanceForDistance(dist);

            if (dropChance > 0 && ThreadLocalRandom.current().nextDouble() < dropChance) {
                logDebug("DROP tiny for RX " + rx.getId() + " dist=" + (int) dist);
                continue;
            }

            byte[] maybe = opusData;

            if (interferenceEnabled && dist > NOISE_CLEAR_RANGE) {
                double f = Math.min(1.0, (dist - NOISE_CLEAR_RANGE) / Math.max(1.0, (farDistance - NOISE_CLEAR_RANGE)));
                if (f < 0) f = 0;

                double targetDb = -60.0 + (60.0 + maxNoiseDb) * f;
                double noiseAmp = Interference.dbToLin(targetDb);

                try { maybe = mixStaticIntoOpus(opusData, noiseAmp, sampleRate); }
                catch (Throwable ignore) {}
            }

            if (delayTicks <= 0) speaker.playFrame(maybe);
            else {
                final byte[] sendData = maybe;
                Bukkit.getScheduler().runTaskLater(plugin, () -> speaker.playFrame(sendData), delayTicks);
            }
        }
    }

    private byte[] mixStaticIntoOpus(byte[] opus, double noiseAmp, int sampleRate) throws Exception {
        Method createDec = api.getClass().getMethod("createDecoder");
        Method createEnc = api.getClass().getMethod("createEncoder");
        Object decoder = createDec.invoke(api);
        Object encoder = createEnc.invoke(api);

        Class<?> decCls = decoder.getClass();
        Class<?> encCls = encoder.getClass();

        Method decode = Arrays.stream(decCls.getMethods())
                .filter(m -> m.getName().equals("decode") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == byte[].class)
                .findFirst().orElseThrow(() -> new NoSuchMethodException("Decoder#decode(byte[])"));

        Method encode = Arrays.stream(encCls.getMethods())
                .filter(m -> m.getName().equals("encode") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == short[].class)
                .findFirst().orElseThrow(() -> new NoSuchMethodException("Encoder#encode(short[])"));

        short[] pcm = (short[]) decode.invoke(decoder, opus);

        new Interference(sampleRate).mixStatic(pcm, noiseAmp);

        return (byte[]) encode.invoke(encoder, pcm);
    }

    // distance profile
    private static final int DELAY_CLEAR_RANGE = 80;
    private static final int NOISE_CLEAR_RANGE = 600;
    private static final double DELAY_PER_BLOCK = 0.0033;
    private static final double JITTER_PCT = 0.05;

    private static final double D0 = NOISE_CLEAR_RANGE, P0 = 0.00;
    private static final double D1 = 2700.0,            P1 = 0.00;
    private static final double D2 = 3300.0,            P2 = 0.02;
    private static final double D3 = 3900.0,            P3 = 0.08;
    private static final double D4 = 4800.0,            P4 = 0.15;
    private static final double D5 = 6000.0,            P5 = 0.25;

    private long delayTicksForDistance(double d) {
        if (d <= DELAY_CLEAR_RANGE) return 0;
        double extra = d - DELAY_CLEAR_RANGE;
        double sec = extra * DELAY_PER_BLOCK;
        if (JITTER_PCT > 0) {
            double j = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * JITTER_PCT;
            sec = Math.max(0.0, sec * (1.0 + j));
        }
        return Math.max(0, Math.round(sec * 20.0));
    }

    private double dropChanceForDistance(double d) {
        double p;
        if (d <= D0) p = P0;
        else if (d <= D1) p = lerp(d, D0, P0, D1, P1);
        else if (d <= D2) p = lerp(d, D1, P1, D2, P2);
        else if (d <= D3) p = lerp(d, D2, P2, D3, P3);
        else if (d <= D4) p = lerp(d, D3, P3, D4, P4);
        else if (d <= D5) p = lerp(d, D4, P4, D5, P5);
        else p = P5;
        return Math.min(Math.max(p, 0), 0.35);
    }

    private static double lerp(double x, double x0, double y0, double x1, double y1) {
        if (x1 == x0) return y1;
        double t = (x - x0) / (x1 - x0);
        return y0 + t * (y1 - y0);
    }

    public void bindOperator(Radio r, org.bukkit.entity.Player operator) {
        if (api == null) return;
        if (!plugin.getConfig().getBoolean("voice.isolateOperatorInTxGroup", false)) return;

        VoicechatConnection c = api.getConnectionOf(operator.getUniqueId());
        if (c == null) return;
        UUID gid = txGroupIds.get(r.getTransmitFrequency());
        if (gid == null) return;
        Group g = api.getGroup(gid);
        if (g != null) c.setGroup(g);
    }

    public void unbindOperator(UUID operator, int txFreq) {
        if (api == null) return;
        VoicechatConnection c = api.getConnectionOf(operator);
        if (c != null) c.setGroup(null);
    }

    public void updateSpeakerFor(Radio r) {
        if (r.getListenFrequency() < 1) removeSpeaker(r.getId());
    }

    public void removeSpeaker(UUID radioId) {
        LocationalSpeaker sp = speakers.remove(radioId);
        if (sp != null) sp.close();
    }

    public void shutdownAllSpeakers() {
        for (LocationalSpeaker sp : speakers.values()) sp.close();
        speakers.clear();
    }

    private void logDebug(String msg) {
        if (plugin.getConfig().getBoolean("debug.voice", false)) {
            plugin.getLogger().info("[VoiceBridge] " + msg);
        }
    }
}
