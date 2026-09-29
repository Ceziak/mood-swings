package net.ceziak.mood_swings.network;

import net.ceziak.mood_swings.MoodSwings;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Server-to-client request to open the lockdown named-area selector. */
public record OpenLockdownScreenPayload(String json) implements CustomPayload {
    public static final CustomPayload.Id<OpenLockdownScreenPayload> ID =
            new CustomPayload.Id<>(MoodSwings.id("open_lockdown_screen"));
    public static final PacketCodec<RegistryByteBuf, OpenLockdownScreenPayload> CODEC =
            CustomPayload.codecOf(OpenLockdownScreenPayload::write, OpenLockdownScreenPayload::new);

    private OpenLockdownScreenPayload(RegistryByteBuf buf) {
        this(buf.readString(1_048_576));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(json == null ? "[]" : json, 1_048_576);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
