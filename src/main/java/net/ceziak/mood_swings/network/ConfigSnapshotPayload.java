package net.ceziak.mood_swings.network;

import net.ceziak.mood_swings.MoodSwings;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Server-to-client snapshot used by the save-aware config menu. */
public record ConfigSnapshotPayload(String json) implements CustomPayload {
    public static final CustomPayload.Id<ConfigSnapshotPayload> ID =
            new CustomPayload.Id<>(MoodSwings.id("config_snapshot"));
    public static final PacketCodec<RegistryByteBuf, ConfigSnapshotPayload> CODEC =
            CustomPayload.codecOf(ConfigSnapshotPayload::write, ConfigSnapshotPayload::new);

    private ConfigSnapshotPayload(RegistryByteBuf buf) {
        this(buf.readString(1_048_576));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(json, 1_048_576);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
