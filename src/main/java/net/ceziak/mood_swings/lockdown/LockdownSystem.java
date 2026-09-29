package net.ceziak.mood_swings.lockdown;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.index.WatheSounds;
import net.ceziak.mood_swings.MoodSwings;
import net.ceziak.mood_swings.area.NamedArea;
import net.ceziak.mood_swings.config.WorldDataStore;
import net.ceziak.mood_swings.network.LockdownAreaOption;
import net.ceziak.mood_swings.network.OpenLockdownScreenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Native-shop integration and server authority for area lockdown purchases. */
public final class LockdownSystem {
    public static final int PRICE = 200;
    public static final int DURATION_SECONDS = 30;
    public static final int DURATION_TICKS = DURATION_SECONDS * 20;
    public static final int COOLDOWN_SECONDS = 120;
    public static final int COOLDOWN_TICKS = COOLDOWN_SECONDS * 20;

    public static final Item LOCKDOWN_ICON = Registry.register(
            Registries.ITEM,
            MoodSwings.id("lockdown"),
            new LockdownItem(new Item.Settings().maxCount(1))
    );

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final LockdownShopEntry SHOP_ENTRY = new LockdownShopEntry();

    private LockdownSystem() {
    }

    /**
     * The item is registered during class initialization. The shop slot itself is
     * added only after the active save's setting is known.
     */
    public static void initialize() {
        setShopEntryEnabled(false);
    }

    /** Keeps Wathe's client and server shop lists aligned with the active save. */
    public static synchronized void setShopEntryEnabled(boolean enabled) {
        GameConstants.SHOP_ENTRIES.removeIf(LockdownShopEntry.class::isInstance);
        if (enabled) {
            GameConstants.SHOP_ENTRIES.add(SHOP_ENTRY);
        }
    }

    /** Opens the selector without charging. The actual purchase happens after a valid area is chosen. */
    public static void openSelector(ServerPlayerEntity player) {
        if (!WorldDataStore.isLockdownEnabled()) {
            purchaseFailed(player, "The Lockdown feature is disabled for this save.");
            return;
        }
        if (!canUse(player)) {
            purchaseFailed(player, "Lockdown is only available to an active killer.");
            return;
        }
        if (isOnCooldown(player)) {
            purchaseFailed(player, "Lockdown is still on cooldown.");
            return;
        }

        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        if (shop.balance < PRICE) {
            purchaseFailed(player, "The player does not have enough money.");
            return;
        }

        List<LockdownAreaOption> options = new ArrayList<>();
        for (Map.Entry<String, NamedArea> entry : LockdownAreaStore.inPlayerDimension(player)) {
            NamedArea area = entry.getValue();
            String displayName = area.displayName() == null || area.displayName().isBlank()
                    ? entry.getKey()
                    : area.displayName();
            options.add(new LockdownAreaOption(entry.getKey(), displayName));
        }
        options.sort(Comparator.comparing(LockdownAreaOption::displayName, String.CASE_INSENSITIVE_ORDER));

        if (!ServerPlayNetworking.canSend(player, OpenLockdownScreenPayload.ID)) {
            purchaseFailed(player, "The client cannot open the Lockdown area selector.");
            return;
        }

        ServerPlayNetworking.send(player, new OpenLockdownScreenPayload(GSON.toJson(options)));
    }

    /** Revalidates every purchase condition, locks the doors, and only then removes the money. */
    public static void activate(ServerPlayerEntity player, String rawAreaId) {
        if (!WorldDataStore.isLockdownEnabled()) {
            purchaseFailed(player, "The Lockdown feature is disabled for this save.");
            return;
        }
        if (!canUse(player)) {
            purchaseFailed(player, "Lockdown is only available to an active killer.");
            return;
        }
        if (isOnCooldown(player)) {
            purchaseFailed(player, "Lockdown is still on cooldown.");
            return;
        }

        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        if (shop.balance < PRICE) {
            purchaseFailed(player, "The player no longer has enough money.");
            return;
        }

        String areaId = WorldDataStore.normalize(rawAreaId);
        NamedArea area = LockdownAreaStore.get(areaId).orElse(null);
        if (area == null) {
            purchaseFailed(player, "The selected Lockdown area no longer exists.");
            return;
        }

        String currentDimension = player.getServerWorld().getRegistryKey().getValue().toString();
        if (!currentDimension.equals(area.dimension())) {
            purchaseFailed(player, "The selected Lockdown area is in another dimension.");
            return;
        }

        int lockedDoors = lockDoors(player.getServerWorld(), area);
        if (lockedDoors <= 0) {
            purchaseFailed(player, "The selected area contains no compatible Wathe doors.");
            return;
        }

        shop.setBalance(shop.balance - PRICE);
        player.getItemCooldownManager().set(LOCKDOWN_ICON, COOLDOWN_TICKS);
        playWatheShopSound(player, WatheSounds.UI_SHOP_BUY);

        String displayName = area.displayName() == null || area.displayName().isBlank()
                ? areaId
                : area.displayName();
        player.sendMessage(
                Text.translatable(
                                "message.mood_swings.lockdown.activated",
                                displayName,
                                DURATION_SECONDS
                        )
                        .formatted(Formatting.RED, Formatting.BOLD),
                true
        );
        MoodSwings.LOGGER.debug(
                "{} activated Lockdown in '{}' for {} seconds; {} doors were jammed.",
                player.getName().getString(),
                areaId,
                DURATION_SECONDS,
                lockedDoors
        );
    }

    private static boolean isOnCooldown(ServerPlayerEntity player) {
        return player.getItemCooldownManager().isCoolingDown(LOCKDOWN_ICON);
    }

    private static boolean canUse(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.isRunning() && game.canUseKillerFeatures(player);
    }

    private static int lockDoors(ServerWorld world, NamedArea area) {
        int locked = 0;
        Set<Long> visited = new HashSet<>();
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                for (int y = area.minY(); y <= area.maxY(); y++) {
                    cursor.set(x, y, z);
                    BlockState state = world.getBlockState(cursor);
                    if (VanillaWatheDoorLocks.isSupported(state)) {
                        // Vanilla doors are two blocks tall. Count and jam only the lower half.
                        if (state.contains(Properties.DOUBLE_BLOCK_HALF)
                                && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                                && visited.add(cursor.asLong())
                                && VanillaWatheDoorLocks.jam(world, cursor, DURATION_TICKS)) {
                            locked++;
                        }
                        continue;
                    }

                    BlockEntity blockEntity = world.getBlockEntity(cursor);
                    if (!(blockEntity instanceof DoorBlockEntity door)
                            || door.isBlasted()
                            || !visited.add(door.getPos().asLong())) {
                        continue;
                    }

                    int remaining = Math.max(door.getJammedTime(), DURATION_TICKS);
                    if (door.isOpen()) {
                        door.toggle(false);
                    }
                    door.setJammed(remaining);
                    door.markDirty();
                    door.sync();
                    locked++;
                }
            }
        }

        return locked;
    }

    /**
     * Uses the same player-only MASTER-channel shop feedback path as Wathe's
     * native store, including its small randomized pitch variation.
     */
    private static void playWatheShopSound(ServerPlayerEntity player, SoundEvent sound) {
        player.playSoundToPlayer(
                sound,
                SoundCategory.MASTER,
                1.0F,
                0.9F + player.getRandom().nextFloat() * 0.2F
        );
    }

    private static void purchaseFailed(ServerPlayerEntity player, String detail) {
        MoodSwings.LOGGER.debug("Lockdown purchase failed for {}: {}", player.getName().getString(), detail);
        player.sendMessage(Text.literal("Purchase Failed").formatted(Formatting.RED), true);
        playWatheShopSound(player, WatheSounds.UI_SHOP_BUY_FAIL);
    }
}
