
package com.civlabs.radios.voice;

import com.civlabs.radios.CivLabsRadiosPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;

public class RadioVoicePlugin implements VoicechatPlugin {
    private final VoiceBridge bridge;

    public RadioVoicePlugin(CivLabsRadiosPlugin plugin, VoiceBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String getPluginId() {
        return "civlabs_radios";
    }

    @Override
    public void registerEvents(EventRegistration reg) {
        reg.registerEvent(VoicechatServerStartedEvent.class, bridge::onServerStarted);
        reg.registerEvent(MicrophonePacketEvent.class, bridge::onMicPacket);
    }
}
