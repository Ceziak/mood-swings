package net.ceziak.mood_swings.network;

import net.ceziak.mood_swings.MoodSwings;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete client OST pool for the player's current dimension.
 * Every listed track remains alive; volume zero means muted rather than stopped.
 */
public record OstAreaStatePayload(
        String dimension,
        List<TrackState> tracks,
        float shakeIntensity,
        float darkness,
        int effectFadeTicks
) implements CustomPayload {
    private static final int MAX_TRACKS = 1024;

    public static final CustomPayload.Id<OstAreaStatePayload> ID =
            new CustomPayload.Id<>(MoodSwings.id("ost_area_state"));
    public static final PacketCodec<RegistryByteBuf, OstAreaStatePayload> CODEC =
            CustomPayload.codecOf(OstAreaStatePayload::write, OstAreaStatePayload::new);

    public OstAreaStatePayload {
        dimension = dimension == null ? "" : dimension;
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        shakeIntensity = Math.clamp(shakeIntensity, 0.0F, 1.0F);
        darkness = Math.clamp(darkness, 0.0F, 1.0F);
        effectFadeTicks = Math.max(0, effectFadeTicks);
        if (tracks.size() > MAX_TRACKS) {
            throw new IllegalArgumentException("Too many OST tracks in one dimension: " + tracks.size());
        }
    }

    private OstAreaStatePayload(RegistryByteBuf buf) {
        this(buf.readString(256), readTracks(buf), buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    private static List<TrackState> readTracks(RegistryByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_TRACKS) {
            throw new IllegalArgumentException("Invalid OST track count: " + count);
        }

        List<TrackState> tracks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tracks.add(new TrackState(buf));
        }
        return List.copyOf(tracks);
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(dimension, 256);
        buf.writeVarInt(tracks.size());
        for (TrackState track : tracks) {
            track.write(buf);
        }
        buf.writeFloat(shakeIntensity);
        buf.writeFloat(darkness);
        buf.writeVarInt(effectFadeTicks);
    }

    public static OstAreaStatePayload stop() {
        return new OstAreaStatePayload("", List.of(), 0.0F, 0.0F, 0);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** One persistent, non-positional sound stream and its currently requested gain. */
    public record TrackState(String soundId, float volume, int fadeTicks) {
        public TrackState {
            soundId = soundId == null ? "" : soundId;
            volume = Math.clamp(volume, 0.0F, 1.0F);
            fadeTicks = Math.max(0, fadeTicks);
        }

        private TrackState(RegistryByteBuf buf) {
            this(buf.readString(256), buf.readFloat(), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(soundId, 256);
            buf.writeFloat(volume);
            buf.writeVarInt(fadeTicks);
        }
    }
}
