package dev.ecpis.ecpisdlc;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
    private boolean autoToolActive;
    private boolean autoToolManualOverride;
    private int autoToolOriginalSlot = -1;
    private int autoToolSelectedSlot = -1;
    private final Map<String, Boolean> bindDown = new HashMap<>();

    ClientFeatures(NativeOverlay overlay) {
        this.overlay = overlay;
    }

    void pollModuleBinds(Minecraft client) {
        long window = client.getWindow().handle();
        boolean gameplay = client.player != null && client.screen == null;
        pollBind(window, gameplay, "AutoTotem", FeatureState.autoTotemBind);
        pollBind(window, gameplay, "AutoTool", FeatureState.autoToolBind);
        pollBind(window, gameplay, "Fullbright", FeatureState.fullbrightBind);
        pollBind(window, gameplay, "HUD", FeatureState.hudBind);
        pollBind(window, gameplay, "Coordinates", FeatureState.coordinatesBind);
        pollBind(window, gameplay, "Ambience", FeatureState.ambienceBind);
    }

    private void pollBind(long window, boolean gameplay, String module, String keyName) {
        int key = glfwKeyCode(keyName);
        boolean down = key != GLFW.GLFW_KEY_UNKNOWN && GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
        boolean wasDown = bindDown.getOrDefault(module, false);
        bindDown.put(module, down);
        if (gameplay && down && !wasDown) overlay.toggleBound(module);
    }

    private static int glfwKeyCode(String keyName) {
        if (keyName == null || keyName.equalsIgnoreCase("NONE")) return GLFW.GLFW_KEY_UNKNOWN;
        String key = keyName.trim().toUpperCase(Locale.ROOT);
        if (key.length() == 1) {
            char character = key.charAt(0);
            if (character >= 'A' && character <= 'Z') return GLFW.GLFW_KEY_A + character - 'A';
            if (character >= '0' && character <= '9') return GLFW.GLFW_KEY_0 + character - '0';
        }
        if (key.length() >= 2 && key.charAt(0) == 'F') {
            try {
                int function = Integer.parseInt(key.substring(1));
                if (function >= 1 && function <= 12) return GLFW.GLFW_KEY_F1 + function - 1;
            } catch (NumberFormatException ignored) {
                return GLFW.GLFW_KEY_UNKNOWN;
            }
        }
        return switch (key) {
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "ENTER", "RETURN" -> GLFW.GLFW_KEY_ENTER;
            case "ESC", "ESCAPE" -> GLFW.GLFW_KEY_ESCAPE;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "BACKSPACE" -> GLFW.GLFW_KEY_BACKSPACE;
            case "SHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "CONTROL", "CTRL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "ALT" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "UP" -> GLFW.GLFW_KEY_UP;
            case "DOWN" -> GLFW.GLFW_KEY_DOWN;
            case "LEFT" -> GLFW.GLFW_KEY_LEFT;
            case "RIGHT" -> GLFW.GLFW_KEY_RIGHT;
            case "PAGEUP" -> GLFW.GLFW_KEY_PAGE_UP;
            case "PAGEDOWN" -> GLFW.GLFW_KEY_PAGE_DOWN;
            case "HOME" -> GLFW.GLFW_KEY_HOME;
            case "END" -> GLFW.GLFW_KEY_END;
            case "INSERT" -> GLFW.GLFW_KEY_INSERT;
            case "DELETE" -> GLFW.GLFW_KEY_DELETE;
            case "CAPSLOCK" -> GLFW.GLFW_KEY_CAPS_LOCK;
            case "COMMA" -> GLFW.GLFW_KEY_COMMA;
            case "PERIOD" -> GLFW.GLFW_KEY_PERIOD;
            case "SLASH" -> GLFW.GLFW_KEY_SLASH;
            case "SEMICOLON" -> GLFW.GLFW_KEY_SEMICOLON;
            case "APOSTROPHE" -> GLFW.GLFW_KEY_APOSTROPHE;
            case "MINUS" -> GLFW.GLFW_KEY_MINUS;
            case "EQUALS", "EQUAL" -> GLFW.GLFW_KEY_EQUAL;
            case "LEFTBRACKET" -> GLFW.GLFW_KEY_LEFT_BRACKET;
            case "RIGHTBRACKET" -> GLFW.GLFW_KEY_RIGHT_BRACKET;
            case "BACKSLASH" -> GLFW.GLFW_KEY_BACKSLASH;
            case "GRAVE" -> GLFW.GLFW_KEY_GRAVE_ACCENT;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }

    void tick(Minecraft client) {
        updateFullbright(client);
        updateAutoTotem(client);
        updateAutoTool(client);
    }

    private void updateFullbright(Minecraft client) {
        if (client.options == null) {
            return;
        }

        int ambienceLighting = Ambience.INSTANCE.isEnabled() ? Ambience.INSTANCE.lighting() : 0;
        if (FeatureState.fullbright || ambienceLighting > 0) {
            if (originalGamma == null) {
                originalGamma = client.options.gamma().get();
            }
            double target = FeatureState.fullbright
                    ? 1.0
                    : originalGamma + (1.0 - originalGamma) * ambienceLighting / 100.0;
            client.options.gamma().set(Math.max(originalGamma, Math.min(1.0, target)));
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
        if (player.getHealth() > FeatureState.autoTotemHealthThreshold) {
            pendingTotemHotbarSlot = -1;
            pendingTotemTicks = 0;
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
                totemSwapCooldown = FeatureState.autoTotemDelayTicks;
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
                totemSwapCooldown = FeatureState.autoTotemDelayTicks;
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

    private void updateAutoTool(Minecraft client) {
        Player player = client.player;
        if (player == null || client.options == null) {
            clearAutoToolState();
            autoToolManualOverride = false;
            return;
        }

        Inventory inventory = player.getInventory();
        int selectedSlot = inventory.getSelectedSlot();
        boolean attacking = client.screen == null && client.options.keyAttack.isDown();
        if (!FeatureState.autoTool || !attacking) {
            if (autoToolActive && selectedSlot == autoToolSelectedSlot && autoToolOriginalSlot >= 0) {
                inventory.setSelectedSlot(autoToolOriginalSlot);
            }
            clearAutoToolState();
            autoToolManualOverride = false;
            return;
        }
        if (autoToolManualOverride) {
            return;
        }
        if (autoToolActive && selectedSlot != autoToolSelectedSlot) {
            clearAutoToolState();
            autoToolManualOverride = true;
            return;
        }
        if (client.level == null || !(client.hitResult instanceof BlockHitResult blockHit)) {
            return;
        }

        BlockState blockState = client.level.getBlockState(blockHit.getBlockPos());
        int bestSlot = selectedSlot;
        float bestSpeed = inventory.getItem(selectedSlot).getDestroySpeed(blockState);
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            float speed = inventory.getItem(slot).getDestroySpeed(blockState);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }

        if (bestSlot != selectedSlot) {
            if (!autoToolActive) {
                autoToolOriginalSlot = selectedSlot;
            }
            inventory.setSelectedSlot(bestSlot);
            autoToolSelectedSlot = bestSlot;
            autoToolActive = true;
        }
    }

    private void clearAutoToolState() {
        autoToolActive = false;
        autoToolOriginalSlot = -1;
        autoToolSelectedSlot = -1;
    }

    void shutdown(Minecraft client) {
        if (client.player != null && autoToolActive
                && client.player.getInventory().getSelectedSlot() == autoToolSelectedSlot
                && autoToolOriginalSlot >= 0) {
            client.player.getInventory().setSelectedSlot(autoToolOriginalSlot);
        }
        clearAutoToolState();
        if (originalGamma != null) {
            client.options.gamma().set(originalGamma);
            originalGamma = null;
        }
    }
}
