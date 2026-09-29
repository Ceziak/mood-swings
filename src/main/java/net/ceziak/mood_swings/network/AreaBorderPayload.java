package net.ceziak.mood_swings.network;

import net.ceziak.mood_swings.MoodSwings;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server-to-client instruction used by the persistent named-area wireframe renderer.
 * Border appearance remains client-configurable, so only geometry is synchronized.
 */
public record AreaBorderPayload(
        byte action,
        String areaId,
        String dimension,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) implements CustomPayload {
    public static final byte SHOW = 0;
    public static final byte HIDE = 1;
    public static final byte CLEAR = 2;

    public static final CustomPayload.Id<AreaBorderPayload> ID =
            new CustomPayload.Id<>(MoodSwings.id("area_border"));
    public static final PacketCodec<RegistryByteBuf, AreaBorderPayload> CODEC =
            CustomPayload.codecOf(AreaBorderPayload::write, AreaBorderPayload::new);

    private AreaBorderPayload(RegistryByteBuf buf) {
        this(
                buf.readByte(),
                buf.readString(256),
                buf.readString(256),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt()
        );
    }

    private void write(RegistryByteBuf buf) {
        buf.writeByte(action);
        buf.writeString(areaId, 256);
        buf.writeString(dimension, 256);
        buf.writeInt(minX);
        buf.writeInt(minY);
        buf.writeInt(minZ);
        buf.writeInt(maxX);
        buf.writeInt(maxY);
        buf.writeInt(maxZ);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
