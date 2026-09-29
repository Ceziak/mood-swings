package net.ceziak.mood_swings.network;

import net.ceziak.mood_swings.MoodSwings;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client-to-server selection of the named area that should be locked down. */
public record ActivateLockdownPayload(String areaId) implements CustomPayload {
    public static final CustomPayload.Id<ActivateLockdownPayload> ID =
            new CustomPayload.Id<>(MoodSwings.id("activate_lockdown"));
    public static final PacketCodec<RegistryByteBuf, ActivateLockdownPayload> CODEC =
            CustomPayload.codecOf(ActivateLockdownPayload::write, ActivateLockdownPayload::new);

    private ActivateLockdownPayload(RegistryByteBuf buf) {
        this(buf.readString(512));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(areaId == null ? "" : areaId, 512);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
