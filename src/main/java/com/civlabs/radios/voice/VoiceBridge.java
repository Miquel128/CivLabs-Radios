
package com.civlabs.radios.voice;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.model.Radio;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class VoiceBridge {
    private final CivLabsRadiosPlugin plugin;
    private VoicechatServerApi api;
    private final Map<Integer, UUID> txGroupIds = new HashMap<>();
    private final Map<UUID, LocationalSpeaker> speakers = new HashMap<>();

    public VoiceBridge(CivLabsRadiosPlugin plugin) {
        this.plugin = plugin;
    }

    public void onServerStarted(VoicechatServerStartedEvent event) {
        this.api = event.getVoicechat();
        
        // Create TX groups for all possible frequencies
        int maxFreq = plugin.getMaxFrequencies();
        for (int f = 1; f <= maxFreq; f++) {
            Group g = api.groupBuilder()
                .setName(plugin.getConfig().getString("voice.txGroupPrefix", "TX ") + f)
                .setPersistent(plugin.getConfig().getBoolean("voice.txGroupPersistent", true))
                .setHidden(plugin.getConfig().getBoolean("voice.txGroupHidden", true))
                .setType(Group.Type.ISOLATED)
                .build();
            txGroupIds.put(f, g.getId());
        }
        
        plugin.log("Voice initialized: " + maxFreq + " TX groups ready.");
    }

    public void onMicPacket(MicrophonePacketEvent event) {
        if (api == null || event.getSenderConnection() == null) return;

        UUID talker = event.getSenderConnection().getPlayer().getUuid();
        Optional<Radio> txOpt = plugin.store().getAll().stream()
            .filter(r -> talker.equals(r.getOperator()) && r.isEnabled())
            .findFirst();

        if (txOpt.isEmpty()) return;

        Radio tx = txOpt.get();
        if (tx.getTransmitFrequency() < 1) return;

        byte[] opusData = event.getPacket().getOpusEncodedData();
        int freq = tx.getTransmitFrequency();
        int maxDist = plugin.getConfig().getInt("speakerRadius", 30);

        for (Radio speakerRadio : plugin.store().listenersOn(freq)) {
            if (speakerRadio.getLocation() == null) continue;

            speakers.computeIfAbsent(
                speakerRadio.getId(),
                id -> new LocationalSpeaker(api, speakerRadio.getLocation(), maxDist)
            ).playFrame(opusData);
        }
    }

    public void bindOperator(Radio r, org.bukkit.entity.Player operator) {
        if (api == null || !plugin.getConfig().getBoolean("voice.isolateOperatorInTxGroup", true)) {
            return;
        }

        VoicechatConnection c = api.getConnectionOf(operator.getUniqueId());
        if (c == null) return;

        UUID gid = txGroupIds.get(r.getTransmitFrequency());
        if (gid != null) {
            Group g = api.getGroup(gid);
            if (g != null) c.setGroup(g);
        }
    }

    public void unbindOperator(UUID operatorId, int txFreq) {
        if (api == null) return;

        VoicechatConnection c = api.getConnectionOf(operatorId);
        if (c != null && c.getGroup() != null) {
            c.setGroup(null);
        }
    }

    public void updateSpeakerFor(Radio r) {
        if (r.getListenFrequency() < 1) {
            removeSpeaker(r.getId());
        }
    }

    public void removeSpeaker(UUID radioId) {
        Optional.ofNullable(speakers.remove(radioId))
            .ifPresent(LocationalSpeaker::close);
    }

    public void shutdownAllSpeakers() {
        speakers.values().forEach(LocationalSpeaker::close);
        speakers.clear();
    }
}
