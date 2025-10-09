package com.civlabs.radios.voice;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import org.bukkit.Location;

import java.util.UUID;

/** Thin wrapper around a locational audio channel that accepts Opus frames. */
public class LocationalSpeaker {

    private final VoicechatServerApi api;
    private LocationalAudioChannel channel;
    private UUID channelId;
    private Position pos;
    private int radius;

    public LocationalSpeaker(VoicechatServerApi api, Location loc, int radius) {
        this.api = api;
        ensureAt(loc, radius);
    }

    public void ensureAt(Location loc, int radius) {
        Position p = api.createPosition(loc.getX(), loc.getY(), loc.getZ());
        boolean needNew = channel == null
                || this.radius != radius
                || pos == null
                || !pos.equals(p);

        if (needNew) {
            close();
            channelId = UUID.randomUUID();
            pos = p;
            this.radius = radius;
            channel = api.createLocationalAudioChannel(channelId, api.fromServerLevel(loc.getWorld()), pos);
            if (channel != null) {
                channel.setDistance(radius);
            }
        }
    }

    public void playFrame(byte[] opusFrame) {
        if (channel != null && opusFrame != null && opusFrame.length > 0) {
            channel.send(opusFrame); // Voice chat expects Opus data
        }
    }

    public void close() {
        // In modern SVC versions, LocationalAudioChannel is auto-closed; no explicit method.
        channel = null;
    }}
