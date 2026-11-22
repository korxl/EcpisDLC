package dev.ecpis.ecpisdlc;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.geom.Area;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HudManager {
    private static final int SNAP_DISTANCE = 8;
    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("ecpisdlc/hud.json");
    private final Map<String, HudElement> elements = new LinkedHashMap<>();
    private final TargetHUD targetHud = new TargetHUD();
    private final PotionHUD potionHud = new PotionHUD();
    private final ModuleListHUD moduleListHud = new ModuleListHUD();
    private final ArmorHUD armorHud = new ArmorHUD();
    private final NotificationHUD notificationHud = new NotificationHUD();
    private final CoordinatesHUD coordinatesHud = new CoordinatesHUD();
    private final HudModule hudModule = new HudModule(this);
    private volatile boolean moduleEnabled = true;
    private volatile boolean manualEditMode;
    private volatile boolean chatEditMode;
    private volatile boolean connected;
    private volatile int viewportWidth = 1280;
    private volatile int viewportHeight = 720;
    private HudElement dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private long renderRevision;
    private BufferedImage renderedFrame;
    private long renderedRevision = -1;
    private int renderedWidth;
    private int renderedHeight;
    private boolean renderedLightTheme;

    public HudManager() {
        elements.put(targetHud.id(), targetHud);
        elements.put(potionHud.id(), potionHud);
        elements.put(moduleListHud.id(), moduleListHud);
        elements.put(armorHud.id(), armorHud);
        elements.put(notificationHud.id(), notificationHud);
        elements.put(coordinatesHud.id(), coordinatesHud);
        loadConfig();
        hudModule.setEnabled(moduleEnabled);
    }

    public HudModule getHudModule() { return hudModule; }
    public TargetHUD targetHud() { return targetHud; }
    public NotificationHUD notificationHud() { return notificationHud; }
    public boolean isModuleEnabled() { return moduleEnabled; }
    public boolean isEditing() { return manualEditMode || chatEditMode; }
    public boolean isManualEditMode() { return manualEditMode; }
    public boolean isConnected() { return connected; }

    public boolean isElementEnabled(String id) {
        HudElement element = elements.get(id);
        return element != null && element.isEnabled();
    }

    public void setElementEnabled(String id, boolean enabled) {
        HudElement element = elements.get(id);
        if (element == null || element.isEnabled() == enabled) return;
        element.setEnabled(enabled);
        invalidateRender();
        saveConfig();
    }

    public void setModuleEnabled(String name, boolean enabled) {
        if ("HUD".equals(name)) setMasterEnabled(enabled);
        if ("Coordinates".equals(name)) setElementEnabled("Coordinates", enabled);
        moduleListHud.setModuleEnabled(name, enabled);
    }

    public void setModuleEnabledStates(Map<String, Boolean> states) {
        states.forEach(this::setModuleEnabled);
        invalidateRender();
    }

    public void setMasterEnabled(boolean enabled) {
        if (moduleEnabled == enabled) return;
        moduleEnabled = enabled;
        hudModule.setEnabled(enabled);
        invalidateRender();
        saveConfig();
    }

    public void setManualEditMode(boolean enabled) {
        manualEditMode = enabled;
        invalidateRender();
    }

    public void toggleManualEditMode() {
        manualEditMode = !manualEditMode;
        invalidateRender();
    }

    public void notify(String message) {
        notificationHud.push(message);
        invalidateRender();
    }

    public void onEntityAttack(Player attacker, Entity target) {
        targetHud.onAttack(attacker, target);
        invalidateRender();
    }

    public void tick(Minecraft client) {
        chatEditMode = client.screen instanceof ChatScreen;
        Player player = client.player;
        connected = player != null && client.level != null;
        if (!connected) {
            targetHud.clearTarget();
            potionHud.setEffects(List.of());
            armorHud.setDurability(List.of());
            coordinatesHud.setCoordinates("---  ---  ---");
        } else {
            updatePlayerData(player);
        }
        for (HudElement element : elements.values()) element.onTick();
        invalidateRender();
    }

    private void updatePlayerData(Player player) {
        List<String> effects = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            String name = effect.getEffect().value().getDisplayName().getString();
            String level = effect.getAmplifier() > 0 ? " " + roman(effect.getAmplifier() + 1) : "";
            int duration = effect.getDuration();
            String time = duration < 0 ? "∞" : String.format(Locale.ROOT, "%d:%02d", duration / 1200, (duration / 20) % 60);
            effects.add(name + level + "  " + time);
        }
        potionHud.setEffects(effects);

        List<ArmorHUD.Durability> armor = new ArrayList<>(5);
        armor.add(readDurability("HEAD", player.getItemBySlot(EquipmentSlot.HEAD)));
        armor.add(readDurability("CHEST", player.getItemBySlot(EquipmentSlot.CHEST)));
        armor.add(readDurability("LEGS", player.getItemBySlot(EquipmentSlot.LEGS)));
        armor.add(readDurability("FEET", player.getItemBySlot(EquipmentSlot.FEET)));
        armor.add(readDurability("HAND", player.getMainHandItem()));
        armorHud.setDurability(armor);

        coordinatesHud.setCoordinates(String.format(Locale.ROOT, "%d  %d  %d",
                (int) Math.floor(player.getX()), (int) Math.floor(player.getY()), (int) Math.floor(player.getZ())));
    }

    private static ArmorHUD.Durability readDurability(String slot, ItemStack stack) {
        if (stack.isEmpty()) return ArmorHUD.Durability.empty(slot);
        return new ArmorHUD.Durability(slot, stack.getDamageValue(), stack.getMaxDamage(), false);
    }

    private static String roman(int value) {
        return switch (value) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(value);
        };
    }

    public void setViewport(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (viewportWidth != safeWidth || viewportHeight != safeHeight) invalidateRender();
        viewportWidth = safeWidth;
        viewportHeight = safeHeight;
        for (HudElement element : elements.values()) {
            int maxX = Math.max(0, viewportWidth - element.width());
            int maxY = Math.max(0, viewportHeight - element.height());
            element.setPosition(Math.max(0, Math.min(maxX, element.x())), Math.max(0, Math.min(maxY, element.y())));
        }
    }

    public BufferedImage renderFrame(int width, int height, boolean lightTheme) {
        setViewport(width, height);
        if (renderedFrame != null && renderedRevision == renderRevision && renderedWidth == width
                && renderedHeight == height && renderedLightTheme == lightTheme) {
            return renderedFrame;
        }
        BufferedImage frame = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = frame.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        render(graphics, width, height, lightTheme);
        graphics.dispose();
        renderedFrame = frame;
        renderedRevision = renderRevision;
        renderedWidth = width;
        renderedHeight = height;
        renderedLightTheme = lightTheme;
        return frame;
    }

    public void render(java.awt.Graphics2D graphics, int width, int height, boolean lightTheme) {
        setViewport(width, height);
        EcpisRenderer renderer = new EcpisRenderer(graphics, lightTheme);
        boolean editing = isEditing();
        for (HudElement element : elements.values()) {
            element.setEditing(editing);
            if (!editing && (!moduleEnabled || !element.isEnabled())) continue;
            element.render(renderer);
            if (editing) {
                renderer.drawRoundRectOutline(element.x() - 3, element.y() - 3, element.width() + 6,
                        element.height() + 6, 14, renderer.accentColor(), 1.2f);
            }
        }
    }

    private void invalidateRender() {
        renderRevision++;
        renderedFrame = null;
    }

    public Shape windowShape(int width, int height) {
        Area area = new Area();
        boolean editing = isEditing();
        for (HudElement element : elements.values()) {
            if (!editing && (!moduleEnabled || !element.isEnabled())) continue;
            int margin = editing ? 5 : 0;
            Rectangle bounds = new Rectangle(element.x() - margin, element.y() - margin,
                    element.width() + margin * 2, element.height() + margin * 2);
            bounds = bounds.intersection(new Rectangle(0, 0, width, height));
            if (!bounds.isEmpty()) area.add(new Area(bounds));
        }
        return area;
    }

    public boolean beginDrag(int mouseX, int mouseY) {
        if (!isEditing()) return false;
        HudElement candidate = null;
        for (HudElement element : elements.values()) {
            if (element.contains(mouseX, mouseY)) candidate = element;
        }
        if (candidate == null) return false;
        dragging = candidate;
        dragOffsetX = mouseX - candidate.x();
        dragOffsetY = mouseY - candidate.y();
        return true;
    }

    public void dragTo(int mouseX, int mouseY) {
        if (dragging == null || !isEditing()) return;
        int nextX = Math.max(0, Math.min(viewportWidth - dragging.width(), mouseX - dragOffsetX));
        int nextY = Math.max(0, Math.min(viewportHeight - dragging.height(), mouseY - dragOffsetY));
        for (HudElement other : elements.values()) {
            if (other == dragging) continue;
            nextX = snapAxis(nextX, dragging.width(), other.x(), other.width());
            nextY = snapAxis(nextY, dragging.height(), other.y(), other.height());
        }
        nextX = Math.max(0, Math.min(viewportWidth - dragging.width(), nextX));
        nextY = Math.max(0, Math.min(viewportHeight - dragging.height(), nextY));
        dragging.setPosition(nextX, nextY);
        invalidateRender();
    }

    public void endDrag() {
        if (dragging == null) return;
        dragging = null;
        invalidateRender();
        saveConfig();
    }

    private static int snapAxis(int position, int size, int otherPosition, int otherSize) {
        int[] corrections = {
                otherPosition - position,
                otherPosition + otherSize - position,
                otherPosition - (position + size),
                otherPosition + otherSize - (position + size)
        };
        int bestCorrection = 0;
        int bestDistance = SNAP_DISTANCE;
        for (int correction : corrections) {
            int distance = Math.abs(correction);
            if (distance <= bestDistance) {
                bestCorrection = correction;
                bestDistance = distance;
            }
        }
        return position + bestCorrection;
    }

    private void loadConfig() {
        if (!Files.isRegularFile(configPath)) return;
        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            moduleEnabled = readBoolean(json, "moduleEnabled", moduleEnabled);
            for (HudElement element : elements.values()) {
                Matcher object = Pattern.compile("\\\"" + Pattern.quote(element.id()) + "\\\"\\s*:\\s*\\{([^}]*)\\}").matcher(json);
                if (!object.find()) continue;
                String body = object.group(1);
                element.setPosition(readInteger(body, "x", element.x()), readInteger(body, "y", element.y()));
                element.setEnabled(readBoolean(body, "enabled", element.isEnabled()));
            }
        } catch (IOException ignored) {
            // Keep defaults if the HUD config is unavailable or malformed.
        }
    }

    private void saveConfig() {
        StringBuilder json = new StringBuilder("{\n  \"moduleEnabled\": ").append(moduleEnabled).append(",\n  \"elements\": {\n");
        int index = 0;
        for (HudElement element : elements.values()) {
            if (index++ > 0) json.append(",\n");
            json.append("    \"").append(element.id()).append("\": {\"x\": ").append(element.x())
                    .append(", \"y\": ").append(element.y()).append(", \"enabled\": ").append(element.isEnabled()).append("}");
        }
        json.append("\n  }\n}\n");
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // HUD remains usable for the current session if persistence is unavailable.
        }
    }

    private static int readInteger(String json, String key, int fallback) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+)").matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private static boolean readBoolean(String json, String key, boolean fallback) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(true|false)").matcher(json);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
    }
}
