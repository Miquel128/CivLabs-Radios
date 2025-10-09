
package com.civlabs.radios.voice;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import org.bukkit.Location;

import java.util.UUID;

public class LocationalSpeaker {
    private final VoicechatServerApi api;
    private LocationalAudioChannel channel;

    public LocationalSpeaker(VoicechatServerApi api, Location loc, int distance) {
        this.api = api;
        this.channel = api.createLocationalAudioChannel(
            UUID.randomUUID(),
            api.fromServerLevel(loc.getWorld()),
            api.createPosition(loc.getX(), loc.getY(), loc.getZ())
        );
        channel.setDistance(distance);
    }

    public void playFrame(byte[] opusData) {
        if (channel != null) {
            channel.send(opusData);
        }
    }

    public void close() {
        if (channel != null) {
            channel.flush();
        }
        channel = null;
    }
}
