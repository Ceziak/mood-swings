package net.ceziak.mood_swings.network;

import net.ceziak.mood_swings.MoodSwings;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Client-to-server actions submitted by the Mood Swings config menu. */
public record ConfigActionPayload(byte action, String key, String value) implements CustomPayload {
    public static final byte REQUEST_SNAPSHOT = 0;
    public static final byte SAVE_TASK = 1;
    public static final byte RESET_TASK = 2;
    public static final byte RENAME_AREA = 3;
    public static final byte DELETE_AREA = 4;
    public static final byte RELOAD_TASKS = 5;
    public static final byte SET_LOCKDOWN_ENABLED = 6;
    public static final byte SET_OST_AREAS_ENABLED = 7;
    public static final byte SAVE_OST_AREA = 8;
    public static final byte DELETE_OST_AREA = 9;
    public static final byte DELETE_LOCKDOWN_AREA = 10;

    public static final CustomPayload.Id<ConfigActionPayload> ID =
            new CustomPayload.Id<>(MoodSwings.id("config_action"));
    public static final PacketCodec<RegistryByteBuf, ConfigActionPayload> CODEC =
            CustomPayload.codecOf(ConfigActionPayload::write, ConfigActionPayload::new);

    private ConfigActionPayload(RegistryByteBuf buf) {
        this(buf.readByte(), buf.readString(512), buf.readString(1_048_576));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeByte(action);
        buf.writeString(key == null ? "" : key, 512);
        buf.writeString(value == null ? "" : value, 1_048_576);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
