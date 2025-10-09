package com.civlabs.radios.voice;

import com.civlabs.radios.CivLabsRadiosPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;

public class RadioVoicePlugin implements VoicechatPlugin {

    private final CivLabsRadiosPlugin plugin;
    private final VoiceBridge bridge;

    public RadioVoicePlugin(CivLabsRadiosPlugin plugin, VoiceBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    @Override
    public String getPluginId() {
        return "civlabs-radios";
    }

    @Override
    public void initialize(VoicechatApi api) {
        plugin.getLogger().info("[CivLabsRadios] Simple Voice Chat API initialized.");
    }

    @Override
    public void registerEvents(EventRegistration reg) {
        reg.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        reg.registerEvent(MicrophonePacketEvent.class, bridge::onMicPacket);
    }

    private void onServerStarted(VoicechatServerStartedEvent e) {
        bridge.onServerStarted(e);
    }
}
