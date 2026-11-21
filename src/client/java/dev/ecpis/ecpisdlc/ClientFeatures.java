package dev.ecpis.ecpisdlc;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.Locale;

final class ClientFeatures {
    private static final int OFFHAND_MENU_SLOT = 45;
    private static final int FIRST_PLAYER_ITEM_SLOT = 9;
    private static final int FIRST_HOTBAR_MENU_SLOT = 36;
    private static final int HOTBAR_SIZE = 9;
    private static final int END_PLAYER_ITEM_SLOT = 45;

    private final NativeOverlay overlay;
    private Double originalGamma;
    private int totemSwapCooldown;
    private int pendingTotemHotbarSlot = -1;
    private int pendingTotemTicks;

    ClientFeatures(NativeOverlay overlay) {
        this.overlay = overlay;
    }

    void tick(Minecraft client) {
        updateFullbright(client);
        updateAutoTotem(client);
        overlay.updateHud(createSnapshot(client), FeatureState.hud);
    }

    private void updateFullbright(Minecraft client) {
        if (client.options == null) {
            return;
        }

        if (FeatureState.fullbright) {
            if (originalGamma == null) {
                originalGamma = client.options.gamma().get();
            }
            client.options.gamma().set(1.0);
        } else if (originalGamma != null) {
            client.options.gamma().set(originalGamma);
            originalGamma = null;
        }
    }

    private void updateAutoTotem(Minecraft client) {
        Player player = client.player;
        if (!FeatureState.autoTotem) {
            pendingTotemHotbarSlot = -1;
            pendingTotemTicks = 0;
            return;
        }
        if (player == null || client.gameMode == null) {
            pendingTotemHotbarSlot = -1;
            pendingTotemTicks = 0;
            return;
        }
        if (client.screen != null || player.containerMenu != player.inventoryMenu) {
            return;
        }

        if (player.inventoryMenu.getSlot(OFFHAND_MENU_SLOT).getItem().is(Items.TOTEM_OF_UNDYING)) {
            pendingTotemHotbarSlot = -1;
            return;
        }

        if (pendingTotemHotbarSlot >= 0) {
            int hotbarSlot = pendingTotemHotbarSlot;
            if (player.inventoryMenu.getSlot(FIRST_HOTBAR_MENU_SLOT + hotbarSlot)
                    .getItem().is(Items.TOTEM_OF_UNDYING)) {
                client.gameMode.handleInventoryMouseClick(
                        player.inventoryMenu.containerId,
                        OFFHAND_MENU_SLOT,
                        hotbarSlot,
                        ClickType.SWAP,
                        player
                );
                pendingTotemHotbarSlot = -1;
                totemSwapCooldown = 10;
                return;
            }
            if (++pendingTotemTicks <= 10) {
                return;
            }
            pendingTotemHotbarSlot = -1;
        }

        if (totemSwapCooldown > 0) {
            totemSwapCooldown--;
            return;
        }

        for (int hotbarSlot = 0; hotbarSlot < HOTBAR_SIZE; hotbarSlot++) {
            if (player.inventoryMenu.getSlot(FIRST_HOTBAR_MENU_SLOT + hotbarSlot)
                    .getItem().is(Items.TOTEM_OF_UNDYING)) {
                client.gameMode.handleInventoryMouseClick(
                        player.inventoryMenu.containerId,
                        OFFHAND_MENU_SLOT,
                        hotbarSlot,
                        ClickType.SWAP,
                        player
                );
                totemSwapCooldown = 10;
                return;
            }
        }

        for (int slot = FIRST_PLAYER_ITEM_SLOT; slot < FIRST_HOTBAR_MENU_SLOT; slot++) {
            if (player.inventoryMenu.getSlot(slot).getItem().is(Items.TOTEM_OF_UNDYING)) {
                int hotbarSlot = player.getInventory().getSelectedSlot();
                client.gameMode.handleInventoryMouseClick(
                        player.inventoryMenu.containerId,
                        slot,
                        hotbarSlot,
                        ClickType.SWAP,
                        player
                );
                pendingTotemHotbarSlot = hotbarSlot;
                pendingTotemTicks = 0;
                return;
            }
        }
    }

    private NativeOverlay.HudSnapshot createSnapshot(Minecraft client) {
        Player player = client.player;
        if (player == null || client.level == null) {
            return NativeOverlay.HudSnapshot.disconnected();
        }

        Inventory inventory = player.getInventory();
        int totems = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (inventory.getItem(slot).is(Items.TOTEM_OF_UNDYING)) {
                totems += inventory.getItem(slot).getCount();
            }
        }
        if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            totems += player.getOffhandItem().getCount();
        }

        String coordinates = String.format(
                Locale.ROOT,
                "%d  %d  %d",
                (int) Math.floor(player.getX()),
                (int) Math.floor(player.getY()),
                (int) Math.floor(player.getZ())
        );
        return new NativeOverlay.HudSnapshot(
                true,
                player.getName().getString(),
                String.format(Locale.ROOT, "%.1f", player.getHealth()),
                Integer.toString(totems),
                coordinates
        );
    }

    void shutdown(Minecraft client) {
        if (originalGamma != null) {
            client.options.gamma().set(originalGamma);
            originalGamma = null;
        }
    }
}
