package dev.ecpis.ecpisdlc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

final class NativeOverlay {
    private static final int MIN_WIDTH = 760;
    private static final int MIN_HEIGHT = 520;
    private static final int DEFAULT_WIDTH = 1020;
    private static final int DEFAULT_HEIGHT = 680;
    private static final int TOP_BAR = 76;
    private Color TEXT = new Color(239, 244, 249);
    private Color MUTED = new Color(135, 149, 163);
    private static final ThemeDef[] THEMES = {
            new ThemeDef("dark", "DARK", new Color(133, 170, 204), new Color(72, 104, 137),
                    new Color(18, 20, 24), new Color(10, 11, 13), new Color(14, 16, 19), new Color(8, 9, 11),
                    new Color(232, 236, 242), new Color(142, 151, 163)),
            new ThemeDef("light", "LIGHT", new Color(63, 103, 133), new Color(118, 157, 181),
                    new Color(249, 252, 254), new Color(229, 237, 243), new Color(241, 246, 249), new Color(230, 238, 243),
                    new Color(30, 43, 53), new Color(99, 116, 128))
    };
    private static final String[] TAB_IDS = {"combat", "movement", "render", "player", "configs", "binds"};
    private static final String[] TAB_NAMES = {"Combat", "Movement", "Render", "Player", "Configs", "Binds"};
    private static final List<ModuleDef> MODULES = createModules();

    private final HudManager hudManager;
    private final AtomicBoolean updatePending = new AtomicBoolean();
    private final OverlayPreferences preferences = OverlayPreferences.load();
    private final Map<String, Boolean> enabled = new LinkedHashMap<>();
    private final Map<String, String> binds = new LinkedHashMap<>();
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, OverlayPreferences.Profile> configs = new LinkedHashMap<>();
    private final Map<String, Float> moduleAnimationProgress = new LinkedHashMap<>();
    private final Map<String, Timer> moduleAnimationTimers = new LinkedHashMap<>();
    private volatile int gameX;
    private volatile int gameY;
    private volatile int gameWidth = 1280;
    private volatile int gameHeight = 720;
    private volatile boolean tabletVisible;
    private volatile boolean disposed;
    private volatile boolean closing;
    private volatile float animationProgress = 1.0f;
    private volatile float contentAnimationProgress = 1.0f;
    private volatile int tabletWidth;
    private volatile int tabletHeight;
    private Color accent;
    private volatile String themeId;
    private Timer animationTimer;
    private Timer contentAnimationTimer;
    private volatile Runnable closeAction = () -> { };
    private String selectedTab;
    private String activeConfig;
    private String selectedModule;
    private String search = "";
    private String configName = "";
    private String waitingBind;

    private TabletPanel tabletPanel;
    private volatile BufferedImage tabletFrame;
    private Timer frameTimer;
    private boolean tabletMouseDown;
    private boolean hudMouseDown;
    private final MinecraftOverlayTexture tabletTexture = new MinecraftOverlayTexture("tablet");
    private final MinecraftOverlayTexture hudTexture = new MinecraftOverlayTexture("hud");

    private enum SettingType { SLIDER, DROPDOWN, CHECKBOX, COLOR, BIND, ACTION }

    private record ThemeDef(String id, String label, Color accent, Color accentEnd, Color windowStart, Color windowEnd,
                            Color sidebarStart, Color sidebarEnd, Color text, Color muted) { }
    private record SettingDef(String name, SettingType type, double min, double max, double initial, String[] options) { }
    private record ModuleDef(String id, String category, String icon, String description, SettingDef[] settings) { }
    private record ModuleHit(Rectangle bounds, String id) { }
    private record SettingHit(Rectangle bounds, ModuleDef module, SettingDef setting) { }
    private record ActionHit(Rectangle bounds, String config, String action) { }

    NativeOverlay(HudManager hudManager) {
        this.hudManager = hudManager;
        tabletWidth = Math.max(MIN_WIDTH, preferences.width);
        tabletHeight = Math.max(MIN_HEIGHT, preferences.height);
        selectedTab = validTab(preferences.tab) ? preferences.tab : "combat";
        themeId = validTheme(preferences.theme) ? preferences.theme : THEMES[0].id();
        accent = currentTheme().accent();
        TEXT = currentTheme().text();
        MUTED = currentTheme().muted();
        activeConfig = preferences.activeConfig;
        for (ModuleDef module : MODULES) {
            boolean defaultEnabled = module.id().equals("HUD") ? hudManager.isModuleEnabled()
                    : module.id().equals("Coordinates") && hudManager.isElementEnabled("Coordinates");
            boolean moduleEnabled = preferences.enabled.getOrDefault(module.id(), defaultEnabled);
            enabled.put(module.id(), moduleEnabled);
            binds.put(module.id(), preferences.binds.getOrDefault(module.id(), defaultBind(module.id())));
            moduleAnimationProgress.put(module.id(), moduleEnabled ? 1.0f : 0.0f);
            for (SettingDef setting : module.settings()) {
                String key = settingKey(module, setting);
                Object initial = switch (setting.type()) {
                    case SLIDER -> setting.initial();
                    case DROPDOWN -> 0;
                    case CHECKBOX -> module.id().equals("HUD")
                            ? hudManager.isElementEnabled(hudElementId(setting.name())) ? 1.0 : 0.0
                            : setting.initial();
                    case COLOR -> palette()[0];
                    case BIND -> "NONE";
                    case ACTION -> Boolean.FALSE;
                };
                values.put(key, readSetting(setting, preferences.settings.get(key), initial));
            }
        }
        preferences.configs.forEach((name, packed) -> configs.put(name, OverlayPreferences.decodeProfile(packed)));
        configs.putIfAbsent("default", snapshotProfile());
        configs.putIfAbsent("pvp", snapshotProfile());
        configs.putIfAbsent("server1", snapshotProfile());
        if (!configs.containsKey(activeConfig)) {
            activeConfig = "default";
        }
        applySupportedFeatures();
    }

    void show(int x, int y, int width, int height, Runnable closeAction) {
        this.closeAction = closeAction;
        if (!tabletVisible) animationProgress = 0.0f;
        tabletVisible = true;
        closing = false;
        updateWindowBounds(x, y, width, height);
        SwingUtilities.invokeLater(() -> animateTablet(true, null));
    }

    void hideTablet(Runnable onHidden) {
        hudManager.setManualEditMode(false);
        if (!tabletVisible) {
            scheduleUpdate();
            if (onHidden != null) onHidden.run();
            return;
        }
        SwingUtilities.invokeLater(() -> animateTablet(false, onHidden));
    }

    void refreshHud() {
        scheduleUpdate();
    }

    void updateWindowBounds(int x, int y, int width, int height) {
        gameX = x;
        gameY = y;
        if (width > 0) gameWidth = width;
        if (height > 0) gameHeight = height;
        scheduleUpdate();
    }

    void drawTablet(GuiGraphics graphics, int guiWidth, int guiHeight, int guiScale) {
        if (!tabletVisible && !closing) return;
        BufferedImage frame = tabletFrame;
        if (frame == null) return;
        double scale = tabletDisplayScale(guiWidth, guiHeight, guiScale);
        int width = Math.max(1, (int) Math.round(frame.getWidth() / scale));
        int height = Math.max(1, (int) Math.round(frame.getHeight() / scale));
        tabletTexture.draw(graphics, frame, (guiWidth - width) / 2, (guiHeight - height) / 2, width, height);
    }

    void renderHud(GuiGraphics graphics) {
        if (!hudManager.isEditing()
                && (!hudManager.isModuleEnabled() || !hudManager.isConnected() || tabletVisible)) return;
        var window = Minecraft.getInstance().getWindow();
        int width = Math.max(1, window.getScreenWidth());
        int height = Math.max(1, window.getScreenHeight());
        BufferedImage frame = hudManager.renderFrame(width, height, "light".equals(themeId));
        hudTexture.draw(graphics, frame, 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    void mouseClicked(double mouseX, double mouseY, int button, int guiWidth, int guiHeight, int guiScale) {
        double scale = tabletDisplayScale(guiWidth, guiHeight, guiScale);
        int displayWidth = Math.max(1, (int) Math.round(tabletWidth / scale));
        int displayHeight = Math.max(1, (int) Math.round(tabletHeight / scale));
        int left = (guiWidth - displayWidth) / 2;
        int top = (guiHeight - displayHeight) / 2;
        if (mouseX >= left && mouseX < left + displayWidth && mouseY >= top && mouseY < top + displayHeight) {
            tabletMouseDown = true;
            dispatchPanelMouse(MouseEvent.MOUSE_PRESSED, (int) Math.round((mouseX - left) * scale),
                    (int) Math.round((mouseY - top) * scale), button);
        } else if (button == 0 && hudManager.isEditing()) {
            int hudScale = Math.max(1, guiScale);
            hudMouseDown = hudManager.beginDrag((int) Math.round(mouseX * hudScale), (int) Math.round(mouseY * hudScale));
        }
    }

    void mouseDragged(double mouseX, double mouseY, int button, int guiWidth, int guiHeight, int guiScale) {
        if (tabletMouseDown) {
            double scale = tabletDisplayScale(guiWidth, guiHeight, guiScale);
            int x = (int) Math.round((mouseX - (guiWidth - tabletWidth / scale) / 2.0) * scale);
            int y = (int) Math.round((mouseY - (guiHeight - tabletHeight / scale) / 2.0) * scale);
            dispatchPanelMouse(MouseEvent.MOUSE_DRAGGED, x, y, button);
        } else if (hudMouseDown) {
            int scale = Math.max(1, guiScale);
            hudManager.dragTo((int) Math.round(mouseX * scale), (int) Math.round(mouseY * scale));
        }
    }

    void mouseReleased(double mouseX, double mouseY, int button, int guiWidth, int guiHeight, int guiScale) {
        if (tabletMouseDown) {
            double scale = tabletDisplayScale(guiWidth, guiHeight, guiScale);
            int x = (int) Math.round((mouseX - (guiWidth - tabletWidth / scale) / 2.0) * scale);
            int y = (int) Math.round((mouseY - (guiHeight - tabletHeight / scale) / 2.0) * scale);
            dispatchPanelMouse(MouseEvent.MOUSE_RELEASED, x, y, button);
            tabletMouseDown = false;
        }
        if (hudMouseDown) {
            hudManager.endDrag();
            hudMouseDown = false;
        }
    }

    void mouseMoved(double mouseX, double mouseY, int guiWidth, int guiHeight, int guiScale) {
        double scale = tabletDisplayScale(guiWidth, guiHeight, guiScale);
        int x = (int) Math.round((mouseX - (guiWidth - tabletWidth / scale) / 2.0) * scale);
        int y = (int) Math.round((mouseY - (guiHeight - tabletHeight / scale) / 2.0) * scale);
        dispatchPanelMouse(MouseEvent.MOUSE_MOVED, x, y, MouseEvent.NOBUTTON);
    }

    private double tabletDisplayScale(int guiWidth, int guiHeight, int guiScale) {
        return Math.max(Math.max(1, guiScale), Math.max(
                tabletWidth / (double) Math.max(1, guiWidth),
                tabletHeight / (double) Math.max(1, guiHeight)
        ));
    }

    void keyPressed(int key, int scancode, int modifiers) {
        int keyCode = awtKeyCode(key, scancode);
        if (keyCode == KeyEvent.VK_UNDEFINED) return;
        int location = key == GLFW.GLFW_KEY_RIGHT_SHIFT ? KeyEvent.KEY_LOCATION_RIGHT : KeyEvent.KEY_LOCATION_STANDARD;
        SwingUtilities.invokeLater(() -> {
            if (tabletPanel == null) return;
            KeyEvent event = new KeyEvent(tabletPanel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                    keyCode, KeyEvent.CHAR_UNDEFINED, location);
            for (java.awt.event.KeyListener listener : tabletPanel.getKeyListeners()) listener.keyPressed(event);
        });
    }

    void charTyped(int codepoint, int modifiers) {
        char[] characters = Character.toChars(codepoint);
        SwingUtilities.invokeLater(() -> {
            if (tabletPanel == null) return;
            for (char character : characters) {
                KeyEvent event = new KeyEvent(tabletPanel, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
                        KeyEvent.VK_UNDEFINED, character);
                for (java.awt.event.KeyListener listener : tabletPanel.getKeyListeners()) listener.keyTyped(event);
            }
        });
    }

    private void dispatchPanelMouse(int id, int x, int y, int button) {
        SwingUtilities.invokeLater(() -> {
            if (tabletPanel == null) return;
            int awtButton = id == MouseEvent.MOUSE_DRAGGED || id == MouseEvent.MOUSE_MOVED
                    ? MouseEvent.NOBUTTON : awtMouseButton(button);
            int modifiers = id == MouseEvent.MOUSE_DRAGGED && button == 0 ? InputEvent.BUTTON1_DOWN_MASK : 0;
            MouseEvent event = new MouseEvent(tabletPanel, id, System.currentTimeMillis(), modifiers,
                    x, y, 1, false, awtButton);
            if (id == MouseEvent.MOUSE_PRESSED) {
                for (java.awt.event.MouseListener listener : tabletPanel.getMouseListeners()) listener.mousePressed(event);
            } else if (id == MouseEvent.MOUSE_RELEASED) {
                for (java.awt.event.MouseListener listener : tabletPanel.getMouseListeners()) listener.mouseReleased(event);
            } else if (id == MouseEvent.MOUSE_DRAGGED) {
                for (java.awt.event.MouseMotionListener listener : tabletPanel.getMouseMotionListeners()) listener.mouseDragged(event);
            } else if (id == MouseEvent.MOUSE_MOVED) {
                for (java.awt.event.MouseMotionListener listener : tabletPanel.getMouseMotionListeners()) listener.mouseMoved(event);
            }
        });
    }

    private static int awtMouseButton(int button) {
        return switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> MouseEvent.BUTTON1;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> MouseEvent.BUTTON3;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> MouseEvent.BUTTON2;
            default -> MouseEvent.NOBUTTON;
        };
    }

    private static int awtKeyCode(int key, int scancode) {
        if ((key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z)
                || (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9)) return key;
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) return KeyEvent.VK_F1 + key - GLFW.GLFW_KEY_F1;
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) return KeyEvent.VK_NUMPAD0 + key - GLFW.GLFW_KEY_KP_0;
        return switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> KeyEvent.VK_ESCAPE;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> KeyEvent.VK_ENTER;
            case GLFW.GLFW_KEY_BACKSPACE -> KeyEvent.VK_BACK_SPACE;
            case GLFW.GLFW_KEY_TAB -> KeyEvent.VK_TAB;
            case GLFW.GLFW_KEY_SPACE -> KeyEvent.VK_SPACE;
            case GLFW.GLFW_KEY_DELETE -> KeyEvent.VK_DELETE;
            case GLFW.GLFW_KEY_INSERT -> KeyEvent.VK_INSERT;
            case GLFW.GLFW_KEY_HOME -> KeyEvent.VK_HOME;
            case GLFW.GLFW_KEY_END -> KeyEvent.VK_END;
            case GLFW.GLFW_KEY_PAGE_UP -> KeyEvent.VK_PAGE_UP;
            case GLFW.GLFW_KEY_PAGE_DOWN -> KeyEvent.VK_PAGE_DOWN;
            case GLFW.GLFW_KEY_LEFT -> KeyEvent.VK_LEFT;
            case GLFW.GLFW_KEY_RIGHT -> KeyEvent.VK_RIGHT;
            case GLFW.GLFW_KEY_UP -> KeyEvent.VK_UP;
            case GLFW.GLFW_KEY_DOWN -> KeyEvent.VK_DOWN;
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> KeyEvent.VK_SHIFT;
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> KeyEvent.VK_CONTROL;
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> KeyEvent.VK_ALT;
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> KeyEvent.VK_META;
            default -> {
                String name = GLFW.glfwGetKeyName(key, scancode);
                yield name != null && name.length() == 1
                        ? KeyEvent.getExtendedKeyCodeForChar(Character.toUpperCase(name.charAt(0)))
                        : KeyEvent.VK_UNDEFINED;
            }
        };
    }

    private void scheduleUpdate() {
        if (disposed || !updatePending.compareAndSet(false, true)) return;
        SwingUtilities.invokeLater(() -> {
            updatePending.set(false);
            if (disposed) return;
            ensurePanel();
            applyWindowState();
        });
    }

    private void ensurePanel() {
        if (tabletPanel != null) return;
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // The overlay is custom-painted and does not depend on a Swing look and feel.
        }
        tabletPanel = new TabletPanel();
        tabletPanel.setPreferredSize(new Dimension(tabletWidth, tabletHeight));
        tabletPanel.setSize(tabletWidth, tabletHeight);
    }

    private void captureTabletFrame() {
        if (disposed || tabletPanel == null || (!tabletVisible && !closing)) return;
        BufferedImage frame = new BufferedImage(tabletWidth, tabletHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = frame.createGraphics();
        tabletPanel.setSize(tabletWidth, tabletHeight);
        tabletPanel.paint(graphics);
        graphics.dispose();
        tabletFrame = frame;
    }

    private void animateTablet(boolean opening, Runnable onFinished) {
        if (disposed) return;
        if (animationTimer != null) animationTimer.stop();
        closing = !opening;
        float from = animationProgress;
        float to = opening ? 1.0f : 0.0f;
        long started = System.nanoTime();
        animationTimer = new Timer(16, event -> {
            float time = Math.min(1.0f, (System.nanoTime() - started) / 360_000_000.0f);
            float eased = smoothStep(time);
            animationProgress = from + (to - from) * eased;
            if (tabletPanel != null) tabletPanel.repaint();
            if (time >= 1.0f) {
                animationTimer.stop();
                animationTimer = null;
                animationProgress = to;
                closing = false;
                if (!opening) {
                    tabletVisible = false;
                    if (onFinished != null) onFinished.run();
                }
                applyWindowState();
            }
        });
        animationTimer.start();
        scheduleUpdate();
    }

    private void applyWindowState() {
        if (tabletPanel == null) return;
        tabletPanel.setPreferredSize(new Dimension(tabletWidth, tabletHeight));
        tabletPanel.setSize(tabletWidth, tabletHeight);
        tabletPanel.repaint();
        if (tabletVisible || closing) {
            if (frameTimer == null) {
                frameTimer = new Timer(33, event -> captureTabletFrame());
                frameTimer.start();
            }
            captureTabletFrame();
        } else if (frameTimer != null) {
            frameTimer.stop();
            frameTimer = null;
        }
    }

    private void persist() {
        preferences.width = tabletWidth;
        preferences.height = tabletHeight;
        preferences.tab = selectedTab;
        preferences.theme = themeId;
        preferences.activeConfig = activeConfig;
        if (activeConfig != null) configs.put(activeConfig, snapshotProfile());
        preferences.enabled.clear();
        preferences.enabled.putAll(enabled);
        preferences.binds.clear();
        preferences.binds.putAll(binds);
        preferences.settings.clear();
        values.forEach((key, value) -> preferences.settings.put(key, encodeSetting(value)));
        preferences.configs.clear();
        configs.forEach((name, profile) -> preferences.configs.put(name, encodeProfile(profile)));
        preferences.save();
    }

    private ThemeDef currentTheme() {
        for (ThemeDef theme : THEMES) if (theme.id().equals(themeId)) return theme;
        return THEMES[0];
    }

    private static boolean validTheme(String id) {
        for (ThemeDef theme : THEMES) if (theme.id().equals(id)) return true;
        return false;
    }

    private Color accentAlpha(int alpha) {
        return new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), alpha);
    }

    private Color surfaceOverlay(int alpha) {
        return "light".equals(themeId) ? new Color(27, 47, 61, alpha) : new Color(255, 255, 255, alpha);
    }

    private void setTheme(String id) {
        if (!validTheme(id) || themeId.equals(id)) return;
        themeId = id;
        accent = currentTheme().accent();
        TEXT = currentTheme().text();
        MUTED = currentTheme().muted();
        persist();
        scheduleUpdate();
        if (tabletPanel != null) tabletPanel.repaint();
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static boolean isImplemented(String moduleId) {
        return moduleId.equals("AutoTotem") || moduleId.equals("AutoTool") || moduleId.equals("Fullbright")
                || moduleId.equals("HUD") || moduleId.equals("Coordinates") || moduleId.equals("Ambience");
    }

    private void applySupportedFeatures() {
        FeatureState.autoTotem = enabled.getOrDefault("AutoTotem", false);
        FeatureState.autoTool = enabled.getOrDefault("AutoTool", false);
        FeatureState.fullbright = enabled.getOrDefault("Fullbright", false);
        FeatureState.hud = enabled.getOrDefault("HUD", true);
        FeatureState.coordinates = enabled.getOrDefault("Coordinates", true);
        hudManager.setModuleEnabledStates(enabled);
        for (String element : List.of("Target HUD", "Potion HUD", "Module List", "Armor HUD", "Notifications")) {
            hudManager.setElementEnabled(hudElementId(element), settingNumber("HUD." + element,
                    hudManager.isElementEnabled(hudElementId(element)) ? 1.0 : 0.0) != 0.0);
        }
        FeatureState.autoTotemDelayTicks = Math.max(0, (int) Math.round(settingNumber("AutoTotem.Delay", 10)));
        FeatureState.autoTotemHealthThreshold = settingNumber("AutoTotem.Health", 8);
        FeatureState.autoTotemBind = binds.getOrDefault("AutoTotem", "G");
        FeatureState.autoToolBind = binds.getOrDefault("AutoTool", "T");
        FeatureState.fullbrightBind = binds.getOrDefault("Fullbright", "N");
        FeatureState.hudBind = binds.getOrDefault("HUD", "H");
        FeatureState.coordinatesBind = binds.getOrDefault("Coordinates", "C");
        FeatureState.ambienceBind = binds.getOrDefault("Ambience", "M");

        Ambience ambience = Ambience.INSTANCE;
        ambience.setEnabled(enabled.getOrDefault("Ambience", false));
        ambience.configure(
                (int) Math.round(settingNumber("Ambience.Time", 0)),
                (int) Math.round(settingNumber("Ambience.Weather", 0)),
                (int) Math.round(settingNumber("Ambience.Fog", 0)),
                settingColor("Ambience.Fog Color", new Color(183, 121, 255)).getRGB(),
                settingNumber("Ambience.Clouds", 1) != 0,
                settingColor("Ambience.Cloud Color", Color.WHITE).getRGB(),
                (int) Math.round(settingNumber("Ambience.Lighting", 0))
        );
    }

    private void setEnabled(String id, boolean value) {
        enabled.put(id, value);
        animateModuleToggle(id, value);
        applySupportedFeatures();
        hudManager.notify(id + (value ? " enabled" : " disabled"));
        persist();
    }

    void toggleBound(String id) {
        if (!isImplemented(id)) return;
        SwingUtilities.invokeLater(() -> {
            if (disposed) return;
            setEnabled(id, !enabled.getOrDefault(id, false));
            if (tabletPanel != null) tabletPanel.repaint();
        });
    }

    private void animateModuleToggle(String id, boolean enabledValue) {
        Timer previous = moduleAnimationTimers.remove(id);
        if (previous != null) previous.stop();
        float from = moduleAnimationProgress.getOrDefault(id, enabledValue ? 0.0f : 1.0f);
        float to = enabledValue ? 1.0f : 0.0f;
        long started = System.nanoTime();
        Timer timer = new Timer(16, event -> {
            float time = Math.min(1.0f, (System.nanoTime() - started) / 300_000_000.0f);
            float eased = smoothStep(time);
            moduleAnimationProgress.put(id, from + (to - from) * eased);
            if (tabletPanel != null) tabletPanel.repaint();
            if (time >= 1.0f) {
                ((Timer) event.getSource()).stop();
                moduleAnimationTimers.remove(id);
                moduleAnimationProgress.put(id, to);
            }
        });
        moduleAnimationTimers.put(id, timer);
        timer.start();
    }

    private OverlayPreferences.Profile snapshotProfile() {
        LinkedHashMap<String, Boolean> enabledSnapshot = new LinkedHashMap<>(enabled);
        LinkedHashMap<String, String> settingSnapshot = new LinkedHashMap<>();
        values.forEach((key, value) -> settingSnapshot.put(key, encodeSetting(value)));
        return new OverlayPreferences.Profile(enabledSnapshot, settingSnapshot, new LinkedHashMap<>(binds));
    }

    private static String encodeProfile(OverlayPreferences.Profile profile) {
        return OverlayPreferences.encodeProfile(profile.enabled(), profile.settings(), profile.binds());
    }

    private double settingNumber(String key, double fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private Color settingColor(String key, Color fallback) {
        Object value = values.get(key);
        return value instanceof Color color ? color : fallback;
    }

    private static String encodeSetting(Object value) {
        if (value instanceof Color color) return String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        return String.valueOf(value);
    }

    private static Object readSetting(SettingDef setting, String stored, Object fallback) {
        if (stored == null) return fallback;
        try {
            return switch (setting.type()) {
                case SLIDER -> Math.max(setting.min(), Math.min(setting.max(), Double.parseDouble(stored)));
                case DROPDOWN -> Math.floorMod(Integer.parseInt(stored), setting.options().length);
                case CHECKBOX -> OverlayPreferences.checkboxValue(stored);
                case COLOR -> Color.decode(stored);
                case BIND -> stored;
                case ACTION -> fallback;
            };
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
            return fallback;
        }
    }

    private void resizeTablet(int width, int height, boolean save) {
        tabletWidth = Math.max(MIN_WIDTH, Math.min(1440, width));
        tabletHeight = Math.max(MIN_HEIGHT, Math.min(960, height));
        if (tabletPanel != null) {
            tabletPanel.setPreferredSize(new Dimension(tabletWidth, tabletHeight));
            tabletPanel.setSize(tabletWidth, tabletHeight);
        }
        if (save) persist();
        scheduleUpdate();
    }

    void dispose() {
        disposed = true;
        tabletTexture.close();
        hudTexture.close();
        SwingUtilities.invokeLater(() -> {
            if (animationTimer != null) {
                animationTimer.stop();
                animationTimer = null;
            }
            if (contentAnimationTimer != null) {
                contentAnimationTimer.stop();
                contentAnimationTimer = null;
            }
            if (frameTimer != null) {
                frameTimer.stop();
                frameTimer = null;
            }
            moduleAnimationTimers.values().forEach(Timer::stop);
            moduleAnimationTimers.clear();
            tabletPanel = null;
            tabletFrame = null;
        });
    }

    private static List<ModuleDef> createModules() {
        List<ModuleDef> modules = new ArrayList<>();
        modules.add(module("KillAura", "combat", "KA", "Автоматическая атака по цели",
                slider("Range", 2, 6, 3.2), slider("CPS", 1, 20, 10), dropdown("Rotation", "Normal", "Packet", "Smooth"), dropdown("Target Sort", "Distance", "Health", "Armor")));
        modules.add(module("Velocity", "combat", "V", "Настройка отбрасывания",
                slider("Horizontal", 0, 100, 0), slider("Vertical", 0, 100, 0), dropdown("Mode", "Normal", "Air", "Ground")));
        modules.add(module("Criticals", "combat", "CR", "Режим критических ударов", dropdown("Mode", "Packet", "Jump", "MiniJump")));
        modules.add(module("AutoTotem", "combat", "AT", "Тотем автоматически в левую руку", slider("Delay", 0, 20, 10), slider("Health", 1, 20, 8)));
        modules.add(module("Speed", "movement", "SP", "Управление скоростью движения", dropdown("Mode", "Vanilla", "Strafe"), slider("Speed", 0.1, 3, 1)));
        modules.add(module("Fly", "movement", "FL", "Режим полёта и скорость", dropdown("Mode", "Vanilla", "Creative", "Glide"), slider("Speed", 0.1, 5, 1)));
        modules.add(module("NoFall", "movement", "NF", "Снижение урона от падения", dropdown("Mode", "Packet", "MLG", "Anti")));
        modules.add(module("LongJump", "movement", "LJ", "Усиленный прыжок", slider("Boost", 0.1, 3, 1), dropdown("Mode", "Normal", "Strafe", "Damage")));
        modules.add(module("ESP", "render", "ES", "Подсветка выбранных целей", checkbox("Only Players", true), dropdown("Style", "Box", "Glow", "2D"), color("Color")));
        modules.add(module("Tracers", "render", "TR", "Линии до сущностей", color("Color"), slider("Thickness", 1, 5, 1)));
        modules.add(module("Fullbright", "render", "FB", "Максимальная игровая гамма"));
        modules.add(module("Ambience", "render", "AM", "Локальные время, погода, туман, облака и освещение",
                dropdown("Time", "World", "Dawn", "Noon", "Sunset", "Midnight"),
                dropdown("Weather", "World", "Clear", "Rain", "Storm"),
                dropdown("Fog", "Off", "Soft", "Dense"), color("Fog Color"),
                checkbox("Clouds", true), color("Cloud Color"), slider("Lighting", 0, 100, 0)));
        modules.add(module("NameTags", "render", "NT", "Настройка имён над игроками", slider("Scale", 0.5, 2, 1), color("Color")));
        modules.add(module("HUD", "render", "HUD", "Настройка отдельных HUD-элементов",
                checkbox("Target HUD", true), checkbox("Potion HUD", true), checkbox("Module List", true),
                checkbox("Armor HUD", true), checkbox("Notifications", true), action("Edit HUD")));
        modules.add(module("Coordinates", "render", "XYZ", "Координаты в отдельном HUD"));
        modules.add(module("AutoTool", "player", "TL", "Выбор лучшего инструмента при добыче блока"));
        modules.add(module("FastPlace", "player", "FP", "Ускоренная постановка блоков", slider("Delay", 0, 4, 0)));
        modules.add(module("ChestStealer", "player", "CS", "Быстрый сбор содержимого сундука", slider("Delay", 0, 500, 100), checkbox("Random", true)));
        return List.copyOf(modules);
    }

    private static ModuleDef module(String id, String category, String icon, String description, SettingDef... settings) {
        return new ModuleDef(id, category, icon, description, settings);
    }

    private static SettingDef slider(String name, double min, double max, double initial) {
        return new SettingDef(name, SettingType.SLIDER, min, max, initial, new String[0]);
    }

    private static SettingDef dropdown(String name, String... options) {
        return new SettingDef(name, SettingType.DROPDOWN, 0, 0, 0, options);
    }

    private static SettingDef checkbox(String name, boolean initial) {
        return new SettingDef(name, SettingType.CHECKBOX, 0, 1, initial ? 1 : 0, new String[0]);
    }

    private static SettingDef action(String name) {
        return new SettingDef(name, SettingType.ACTION, 0, 0, 0, new String[0]);
    }

    private static String hudElementId(String name) {
        return switch (name) {
            case "Target HUD" -> "TargetHUD";
            case "Potion HUD" -> "PotionHUD";
            case "Module List" -> "ModuleList";
            case "Armor HUD" -> "ArmorHUD";
            case "Notifications" -> "Notifications";
            default -> throw new IllegalArgumentException("Unknown HUD element: " + name);
        };
    }

    private static SettingDef color(String name) {
        return new SettingDef(name, SettingType.COLOR, 0, 0, 0, new String[0]);
    }

    private static String settingKey(ModuleDef module, SettingDef setting) {
        return module.id() + "." + setting.name();
    }

    private static String defaultBind(String id) {
        return switch (id) {
            case "KillAura" -> "R";
            case "AutoTotem" -> "G";
            case "AutoTool" -> "T";
            case "Fullbright" -> "N";
            case "HUD" -> "H";
            case "Coordinates" -> "C";
            case "Ambience" -> "M";
            case "Speed" -> "V";
            default -> "NONE";
        };
    }

    private static boolean validTab(String tab) {
        for (String id : TAB_IDS) if (id.equals(tab)) return true;
        return false;
    }

    private static String keyName(int keyCode) {
        return KeyEvent.getKeyText(keyCode).toUpperCase(Locale.ROOT).replace(" ", "");
    }

    private static Color blend(Color start, Color end, float amount) {
        float progress = Math.max(0.0f, Math.min(1.0f, amount));
        return new Color(
                Math.round(start.getRed() + (end.getRed() - start.getRed()) * progress),
                Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * progress),
                Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * progress),
                Math.round(start.getAlpha() + (end.getAlpha() - start.getAlpha()) * progress)
        );
    }

    private static float smoothStep(float progress) {
        float value = Math.max(0.0f, Math.min(1.0f, progress));
        return value * value * value * (value * (value * 6.0f - 15.0f) + 10.0f);
    }

    private static void rounded(Graphics2D graphics, Color color, int x, int y, int width, int height, int radius) {
        graphics.setColor(color);
        graphics.fillRoundRect(x, y, width, height, radius, radius);
    }

    private static void text(Graphics2D graphics, String value, int x, int y, int size, int style, Color color) {
        graphics.setFont(new Font("Dialog", style, size));
        graphics.setColor(color);
        graphics.drawString(value, x, y);
    }

    private static void drawTabIcon(Graphics2D source, String id, int x, int y, Color color) {
        Graphics2D graphics = (Graphics2D) source.create();
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double path = new Path2D.Double();
        switch (id) {
            case "combat" -> {
                path.moveTo(x + 4, y + 14); path.lineTo(x + 13, y + 5); path.lineTo(x + 16, y + 2);
                path.moveTo(x + 2, y + 9); path.lineTo(x + 9, y + 16); path.moveTo(x + 5, y + 6); path.lineTo(x + 12, y + 13);
                path.moveTo(x + 2, y + 14); path.lineTo(x + 5, y + 17);
            }
            case "movement" -> {
                path.moveTo(x + 2, y + 5); path.curveTo(x + 6, y + 1, x + 10, y + 9, x + 15, y + 5);
                path.moveTo(x + 2, y + 11); path.curveTo(x + 6, y + 7, x + 10, y + 15, x + 15, y + 11);
                path.moveTo(x + 2, y + 16); path.lineTo(x + 5, y + 16);
            }
            case "render" -> {
                path.moveTo(x + 1, y + 9); path.curveTo(x + 5, y + 3, x + 12, y + 3, x + 17, y + 9);
                path.curveTo(x + 12, y + 15, x + 5, y + 15, x + 1, y + 9);
                graphics.draw(path);
                graphics.fillOval(x + 7, y + 7, 5, 5);
                graphics.dispose();
                return;
            }
            case "player" -> {
                graphics.drawOval(x + 6, y + 2, 6, 6);
                path.moveTo(x + 3, y + 17); path.curveTo(x + 3, y + 11, x + 15, y + 11, x + 15, y + 17);
            }
            case "configs" -> {
                graphics.drawRoundRect(x + 2, y + 2, 12, 14, 2, 2);
                path.moveTo(x + 5, y + 6); path.lineTo(x + 11, y + 6);
                path.moveTo(x + 5, y + 9); path.lineTo(x + 11, y + 9);
                path.moveTo(x + 5, y + 12); path.lineTo(x + 9, y + 12);
            }
            case "binds" -> {
                graphics.drawRoundRect(x + 1, y + 3, 16, 12, 3, 3);
                path.moveTo(x + 4, y + 7); path.lineTo(x + 4, y + 7);
                path.moveTo(x + 8, y + 7); path.lineTo(x + 8, y + 7);
                path.moveTo(x + 12, y + 7); path.lineTo(x + 12, y + 7);
                path.moveTo(x + 4, y + 11); path.lineTo(x + 12, y + 11);
            }
            default -> { }
        }
        graphics.draw(path);
        graphics.dispose();
    }

    private static void drawMagnifier(Graphics2D graphics, int x, int y, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawOval(x, y, 10, 10);
        graphics.drawLine(x + 8, y + 8, x + 14, y + 14);
    }

    private static void drawCloseIcon(Graphics2D graphics, int x, int y, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(x, y, x + 10, y + 10);
        graphics.drawLine(x + 10, y, x, y + 10);
    }

    private static void drawGearIcon(Graphics2D graphics, int x, int y, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(1.4f));
        Path2D.Double gear = new Path2D.Double();
        for (int index = 0; index < 24; index++) {
            double angle = Math.PI * 2.0 * index / 24.0;
            double radius = index % 3 == 0 ? 8.0 : 6.2;
            double px = x + 9 + Math.cos(angle) * radius;
            double py = y + 9 + Math.sin(angle) * radius;
            if (index == 0) gear.moveTo(px, py); else gear.lineTo(px, py);
        }
        gear.closePath();
        graphics.draw(gear);
        graphics.drawOval(x + 6, y + 6, 6, 6);
    }

    private static void drawChevron(Graphics2D graphics, int x, int y, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(x, y, x + 4, y + 4);
        graphics.drawLine(x + 4, y + 4, x + 8, y);
    }

    private static void drawCheckmark(Graphics2D graphics, int x, int y, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(x, y + 4, x + 3, y + 7);
        graphics.drawLine(x + 3, y + 7, x + 9, y);
    }

    private static void drawPlus(Graphics2D graphics, int x, int y, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(x + 5, y, x + 5, y + 10);
        graphics.drawLine(x, y + 5, x + 10, y + 5);
    }

    private void drawThemeIcon(Graphics2D graphics, int x, int y) {
        graphics.setColor(accent);
        graphics.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if ("light".equals(themeId)) {
            graphics.drawOval(x + 4, y + 4, 8, 8);
            for (int angle = 0; angle < 8; angle++) {
                double radians = Math.PI * 2 * angle / 8.0;
                int x1 = x + 8 + (int) (Math.cos(radians) * 7);
                int y1 = y + 8 + (int) (Math.sin(radians) * 7);
                int x2 = x + 8 + (int) (Math.cos(radians) * 9);
                int y2 = y + 8 + (int) (Math.sin(radians) * 9);
                graphics.drawLine(x1, y1, x2, y2);
            }
        } else {
            Path2D.Double crescent = new Path2D.Double();
            crescent.moveTo(x + 12, y + 2);
            crescent.curveTo(x + 5, y + 3, x + 3, y + 12, x + 8, y + 16);
            crescent.curveTo(x + 3, y + 15, x + 1, y + 11, x + 2, y + 7);
            crescent.curveTo(x + 3, y + 3, x + 7, y + 1, x + 12, y + 2);
            graphics.draw(crescent);
        }
    }

    private final class TabletPanel extends JPanel {
        private int mouseX = -1;
        private int mouseY = -1;
        private boolean searchFocused;
        private boolean configNameFocused;
        private boolean resizing;
        private String activeSlider;
        private final List<ModuleHit> moduleHits = new ArrayList<>();
        private final List<SettingHit> settingHits = new ArrayList<>();
        private final List<ActionHit> actionHits = new ArrayList<>();

        TabletPanel() {
            setOpaque(false);
            setFocusable(true);
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent event) {
                    mouseX = event.getX(); mouseY = event.getY(); repaint();
                }
                @Override public void mouseDragged(MouseEvent event) {
                    mouseX = event.getX(); mouseY = event.getY();
                    if (resizing) resizeTablet(event.getX(), event.getY(), false);
                    else if (activeSlider != null) updateSlider(activeSlider, event.getX());
                    repaint();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    requestFocusInWindow();
                    handleClick(event);
                }
                @Override public void mouseReleased(MouseEvent event) {
                    if (resizing || activeSlider != null) persist();
                    resizing = false; activeSlider = null;
                }
            });
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent event) {
                    if (waitingBind != null) {
                        if (event.getKeyCode() == KeyEvent.VK_ESCAPE) waitingBind = null;
                        else {
                            binds.put(waitingBind, keyName(event.getKeyCode()));
                            applySupportedFeatures();
                            persist();
                        }
                        waitingBind = null;
                        repaint();
                        return;
                    }
                    if (event.getKeyCode() == KeyEvent.VK_ESCAPE || (event.getKeyCode() == KeyEvent.VK_SHIFT
                            && event.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT)) {
                        closeAction.run(); return;
                    }
                    if (event.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                        if (searchFocused && !search.isEmpty()) search = search.substring(0, search.length() - 1);
                        else if (configNameFocused && !configName.isEmpty()) configName = configName.substring(0, configName.length() - 1);
                        repaint();
                    } else if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                        if (configNameFocused) saveNamedConfig();
                        searchFocused = false; configNameFocused = false; repaint();
                    }
                }
                @Override public void keyTyped(KeyEvent event) {
                    char character = event.getKeyChar();
                    if (Character.isISOControl(character)) return;
                    if (searchFocused && search.length() < 32) search += character;
                    else if (configNameFocused && configName.length() < 20) configName += character;
                    repaint();
                }
            });
        }

        @Override protected void paintComponent(Graphics source) {
            super.paintComponent(source);
            Graphics2D graphics = (Graphics2D) source.create();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            moduleHits.clear(); settingHits.clear(); actionHits.clear();

            float progress = Math.max(0.0f, Math.min(1.0f, animationProgress));
            double scale = 0.94 + 0.06 * progress;
            graphics.setComposite(AlphaComposite.SrcOver.derive(progress));
            graphics.translate(width * (1.0 - scale) / 2.0, height * (1.0 - scale) / 2.0);
            graphics.scale(scale, scale);

            for (int spread = 6; spread > 0; spread--) {
                graphics.setColor(new Color(0, 0, 0, 9));
                graphics.fillRoundRect(spread / 2, spread / 2, width - spread, height - spread, 26, 26);
            }
            graphics.setPaint(new GradientPaint(0, 0, currentTheme().windowStart(), width, height, currentTheme().windowEnd()));
            graphics.fillRoundRect(0, 0, width - 1, height - 1, 22, 22);
            graphics.setPaint(new java.awt.RadialGradientPaint(
                    new Point2D.Float(width - 80, 40), Math.max(180, width * 0.62f),
                    new float[]{0.0f, 1.0f}, new Color[]{accentAlpha(25), new Color(0, 0, 0, 0)}));
            graphics.fillRoundRect(0, 0, width - 1, height - 1, 22, 22);
            graphics.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 45));
            graphics.drawRoundRect(0, 0, width - 1, height - 1, 22, 22);
            graphics.setClip(new RoundRectangle2D.Double(0, 0, width, height, 22, 22));
            drawTopBar(graphics, width);
            drawSidebar(graphics, height);
            graphics.setColor(surfaceOverlay(17));
            graphics.drawLine(188, TOP_BAR, 188, height - 18);
            Graphics2D contentGraphics = (Graphics2D) graphics.create();
            float contentProgress = Math.max(0.0f, Math.min(1.0f, contentAnimationProgress));
            contentGraphics.setComposite(AlphaComposite.SrcOver.derive(progress * contentProgress));
            contentGraphics.translate(0, (1.0 - contentProgress) * 14.0);
            drawContent(contentGraphics, width, height);
            contentGraphics.dispose();
            drawResizeGrip(graphics, width, height);
            graphics.dispose();
        }

        private void drawTopBar(Graphics2D graphics, int width) {
            graphics.setColor(surfaceOverlay(18));
            graphics.drawLine(0, TOP_BAR - 1, width, TOP_BAR - 1);
            graphics.setPaint(new GradientPaint(25, 18, currentTheme().accent(), 62, 55, currentTheme().accentEnd()));
            graphics.fillRoundRect(22, 18, 42, 42, 13, 13);
            text(graphics, "E", 36, 49, 26, Font.BOLD, Color.WHITE);
            text(graphics, "EcpisDLC", 76, 36, 15, Font.BOLD, TEXT);
            text(graphics, "CLIENT OVERLAY", 77, 52, 8, Font.BOLD, MUTED);

            int searchX = 248;
            int searchWidth = Math.max(190, Math.min(390, width - 600));
            rounded(graphics, searchFocused ? accentAlpha(22) : surfaceOverlay(10), searchX, 21, searchWidth, 34, 10);
            graphics.setColor(searchFocused ? accentAlpha(100) : surfaceOverlay(25));
            graphics.drawRoundRect(searchX, 21, searchWidth, 34, 10, 10);
            drawMagnifier(graphics, searchX + 11, 30, MUTED);
            text(graphics, search.isEmpty() ? "Найти модуль..." : search + (searchFocused ? "|" : ""), searchX + 37, 42, 11, Font.PLAIN, search.isEmpty() ? MUTED : TEXT);

            int closeX = width - 56;
            rounded(graphics, surfaceOverlay(11), closeX, 22, 32, 32, 10);
            drawCloseIcon(graphics, closeX + 11, 33, TEXT);
            int configX = width - 212;
            rounded(graphics, accentAlpha(15), configX, 22, 137, 32, 10);
            graphics.setColor(accentAlpha(70));
            graphics.drawRoundRect(configX, 22, 137, 32, 10, 10);
            graphics.setColor(accent); graphics.fillOval(configX + 11, 34, 7, 7);
            text(graphics, "CONFIG  " + activeConfig, configX + 26, 42, 10, Font.BOLD, currentTheme().accent());
            text(graphics, "1.21.11", configX - 63, 42, 9, Font.BOLD, MUTED);
        }

        private void drawSidebar(Graphics2D graphics, int height) {
            text(graphics, "NAVIGATION", 24, 104, 9, Font.BOLD, MUTED);
            for (int index = 0; index < TAB_IDS.length; index++) {
                int y = 120 + index * 48;
                boolean active = selectedTab.equals(TAB_IDS[index]);
                if (active) {
                    rounded(graphics, accentAlpha(20), 14, y, 160, 39, 10);
                    rounded(graphics, accent, 14, y + 8, 3, 23, 3);
                } else if (inside(14, y, 160, 39)) {
                    rounded(graphics, surfaceOverlay(10), 14, y, 160, 39, 10);
                }
                drawTabIcon(graphics, TAB_IDS[index], 28, y + 10, active ? accent : MUTED);
                text(graphics, TAB_NAMES[index], 57, y + 25, 12, active ? Font.BOLD : Font.PLAIN, active ? TEXT : MUTED);
                if (index < 4) {
                    String category = TAB_IDS[index];
                    long count = MODULES.stream().filter(module -> module.category().equals(category)).count();
                    text(graphics, Long.toString(count), 151, y + 24, 9, Font.PLAIN, MUTED);
                }
            }
            int bottom = height - 86;
            rounded(graphics, surfaceOverlay(7), 17, bottom, 154, 70, 11);
            text(graphics, "APPEARANCE", 28, bottom + 17, 8, Font.BOLD, MUTED);
            rounded(graphics, accentAlpha(18), 28, bottom + 27, 126, 27, 8);
            drawThemeIcon(graphics, 34, bottom + 31);
            text(graphics, ("light".equals(themeId) ? "LIGHT" : "DARK") + " MODE", 53, bottom + 45, 9, Font.BOLD, TEXT);
            text(graphics, "ALWAYS ON", 28, bottom + 64, 7, Font.BOLD, MUTED);
        }

        private void drawContent(Graphics2D graphics, int width, int height) {
            String title = selectedTab.equals("configs") ? "Config profiles" : selectedTab.equals("binds") ? "Keybinds" : TAB_NAMES[indexOfTab(selectedTab)] + " modules";
            text(graphics, "ECPISDLC  /  " + selectedTab.toUpperCase(), 212, 105, 9, Font.BOLD, accent);
            text(graphics, title, 210, 134, 22, Font.BOLD, TEXT);
            String subtitle = selectedTab.equals("configs") ? "Сохраняй и переключай наборы настроек" : selectedTab.equals("binds") ? "Нажми на кнопку клавиши, затем назначь новую" : "ЛКМ — переключить  ·  ПКМ или шестерёнка — настройки";
            text(graphics, subtitle, 212, 154, 10, Font.PLAIN, MUTED);
            if (selectedTab.equals("configs")) drawConfigs(graphics, width, height);
            else if (selectedTab.equals("binds")) drawBinds(graphics, width, height);
            else drawModules(graphics, width, height);
            text(graphics, waitingBind == null ? "RIGHT SHIFT / ESC  ·  close" : "Нажми клавишу для " + waitingBind + "  ·  ESC отмена",
                    212, height - 22, 9, Font.BOLD, waitingBind == null ? MUTED : accent);
        }

        private void drawModules(Graphics2D graphics, int width, int height) {
            boolean settingsOpen = selectedModule != null && MODULES.stream().anyMatch(module -> module.id().equals(selectedModule));
            int settingsX = width - 304;
            int mainRight = settingsOpen ? settingsX - 16 : width - 24;
            int available = mainRight - 210;
            List<ModuleDef> filtered = MODULES.stream()
                    .filter(module -> module.category().equals(selectedTab))
                    .filter(module -> search.isBlank() || module.id().toLowerCase().contains(search.toLowerCase())
                            || module.description().toLowerCase().contains(search.toLowerCase()))
                    .toList();
            int columns = Math.max(1, Math.min(4, (available + 14) / 224));
            int gap = 12;
            int cardWidth = Math.max(160, (available - gap * (columns - 1)) / columns);
            int cardHeight = 136;
            int top = 174;
            for (int index = 0; index < filtered.size(); index++) {
                ModuleDef module = filtered.get(index);
                int x = 210 + (index % columns) * (cardWidth + gap);
                int y = top + (index / columns) * (cardHeight + gap);
                drawModuleCard(graphics, module, x, y, cardWidth, cardHeight);
                moduleHits.add(new ModuleHit(new Rectangle(x, y, cardWidth, cardHeight), module.id()));
            }
            if (filtered.isEmpty()) {
                text(graphics, "Модули не найдены", 218, 210, 12, Font.PLAIN, MUTED);
            }
            if (settingsOpen) {
                ModuleDef selected = MODULES.stream().filter(module -> module.id().equals(selectedModule)).findFirst().orElseThrow();
                drawSettingsPanel(graphics, selected, settingsX, height - 36);
            }
        }

        private void drawModuleCard(Graphics2D graphics, ModuleDef module, int x, int y, int cardWidth, int cardHeight) {
            boolean on = enabled.getOrDefault(module.id(), false);
            boolean selected = module.id().equals(selectedModule);
            float progress = moduleAnimationProgress.getOrDefault(module.id(), on ? 1.0f : 0.0f);
            Color fill = blend(surfaceOverlay(7), accentAlpha(18), progress);
            rounded(graphics, inside(x, y, cardWidth, cardHeight) ? currentTheme().sidebarStart() : fill, x, y, cardWidth, cardHeight, 13);
            graphics.setColor(selected ? accentAlpha(110) : blend(surfaceOverlay(20), accentAlpha(72), progress));
            graphics.drawRoundRect(x, y, cardWidth, cardHeight, 13, 13);
            graphics.setPaint(new GradientPaint(x + 13, y + 14, accentAlpha(62), x + 48, y + 48, withAlpha(currentTheme().accentEnd(), 35)));
            graphics.fillRoundRect(x + 14, y + 14, 38, 38, 11, 11);
            text(graphics, module.icon(), x + 20, y + 38, module.icon().length() > 2 ? 9 : 12, Font.BOLD, currentTheme().accent());
            text(graphics, module.id(), x + 14, y + 77, 13, Font.BOLD, TEXT);
            drawWrapped(graphics, module.description(), x + 14, y + 96, cardWidth - 28, 10, MUTED, 2);
            drawSwitch(graphics, x + cardWidth - 51, y + 18, progress);
            rounded(graphics, surfaceOverlay(8), x + cardWidth - 34, y + 57, 20, 20, 7);
            drawGearIcon(graphics, x + cardWidth - 30, y + 57, selected ? accent : MUTED);
            text(graphics, on ? "ON" : "OFF", x + 14, y + 122, 8, Font.BOLD, on ? accent : MUTED);
            String capability = isImplemented(module.id()) ? "IMPLEMENTED" : "UI ONLY";
            graphics.setFont(new Font("Dialog", Font.BOLD, 7));
            graphics.setColor(isImplemented(module.id()) ? accent : MUTED);
            graphics.drawString(capability, x + cardWidth - 14 - graphics.getFontMetrics().stringWidth(capability), y + 122);
            if (selected) rounded(graphics, accent, x + 39, y + 118, 20, 2, 2);
        }

        private void drawSettingsPanel(Graphics2D graphics, ModuleDef module, int x, int bottom) {
            int panelHeight = bottom - 165;
            rounded(graphics, surfaceOverlay(7), x, 166, 280, panelHeight, 14);
            graphics.setColor(surfaceOverlay(23));
            graphics.drawRoundRect(x, 166, 280, panelHeight, 14, 14);
            text(graphics, "MODULE SETTINGS", x + 15, 189, 8, Font.BOLD, accent);
            text(graphics, module.id(), x + 15, 214, 17, Font.BOLD, TEXT);
            text(graphics, enabled.getOrDefault(module.id(), false) ? "ENABLED" : "DISABLED", x + 15, 232, 8, Font.BOLD,
                    enabled.getOrDefault(module.id(), false) ? accent : MUTED);
            drawSwitch(graphics, x + 231, 198, moduleAnimationProgress.getOrDefault(module.id(), enabled.getOrDefault(module.id(), false) ? 1.0f : 0.0f));
            moduleHits.add(new ModuleHit(new Rectangle(x + 224, 190, 54, 38), module.id()));
            settingHits.clear();
            int y = 254;
            for (SettingDef setting : module.settings()) {
                int rowHeight = setting.type() == SettingType.COLOR ? 54
                        : setting.type() == SettingType.CHECKBOX ? 34
                        : setting.type() == SettingType.ACTION ? 38 : 48;
                if (y + rowHeight > bottom - 8) break;
                drawSetting(graphics, module, setting, x + 14, y, 252);
                y += rowHeight;
            }
            if (module.settings().length == 0) {
                text(graphics, "У этого модуля нет параметров.", x + 15, 275, 10, Font.PLAIN, MUTED);
            }
        }

        private void drawSetting(Graphics2D graphics, ModuleDef module, SettingDef setting, int x, int y, int width) {
            String key = settingKey(module, setting);
            text(graphics, setting.name(), x, y + 12, 10, Font.BOLD, TEXT);
            Rectangle bounds;
            switch (setting.type()) {
                case SLIDER -> {
                    double value = ((Number) values.get(key)).doubleValue();
                    text(graphics, format(value), x + width - 38, y + 12, 9, Font.BOLD, accent);
                    int trackX = x, trackY = y + 25, trackWidth = width;
                    rounded(graphics, surfaceOverlay(19), trackX, trackY, trackWidth, 5, 5);
                    double ratio = Math.max(0, Math.min(1, (value - setting.min()) / (setting.max() - setting.min())));
                    rounded(graphics, accent, trackX, trackY, (int) (trackWidth * ratio), 5, 5);
                    int knobX = trackX + (int) (trackWidth * ratio) - 6;
                    graphics.setColor(Color.WHITE); graphics.fillOval(knobX, trackY - 4, 13, 13);
                    bounds = new Rectangle(trackX, trackY - 6, trackWidth, 18);
                }
                case DROPDOWN -> {
                    int selected = ((Number) values.get(key)).intValue();
                    String value = setting.options()[Math.floorMod(selected, setting.options().length)];
                    bounds = new Rectangle(x + 105, y - 1, width - 105, 25);
                    rounded(graphics, surfaceOverlay(10), bounds.x, bounds.y, bounds.width, bounds.height, 7);
                    text(graphics, value, bounds.x + 10, bounds.y + 17, 9, Font.BOLD, TEXT);
                    drawChevron(graphics, bounds.x + bounds.width - 18, bounds.y + 9, MUTED);
                }
                case CHECKBOX -> {
                    boolean checked = ((Number) values.get(key)).doubleValue() != 0;
                    bounds = new Rectangle(x + width - 24, y - 1, 22, 22);
                    rounded(graphics, checked ? accentAlpha(50) : surfaceOverlay(12), bounds.x, bounds.y, 20, 20, 6);
                    graphics.setColor(checked ? accent : surfaceOverlay(35)); graphics.drawRoundRect(bounds.x, bounds.y, 20, 20, 6, 6);
                    if (checked) drawCheckmark(graphics, bounds.x + 5, bounds.y + 6, accent);
                }
                case COLOR -> {
                    bounds = new Rectangle(x, y + 20, width, 23);
                    Color[] palette = palette();
                    Color selected = (Color) values.computeIfAbsent(key, ignored -> palette[0]);
                    for (int index = 0; index < palette.length; index++) {
                        int swatchX = x + index * 29;
                        graphics.setColor(palette[index]); graphics.fillRoundRect(swatchX, y + 21, 20, 17, 6, 6);
                        if (selected.equals(palette[index])) {
                            graphics.setColor(currentTheme().accent()); graphics.drawRoundRect(swatchX - 2, y + 19, 23, 21, 7, 7);
                        }
                    }
                }
                case BIND -> {
                    bounds = new Rectangle(x + 105, y - 1, width - 105, 25);
                    rounded(graphics, surfaceOverlay(10), bounds.x, bounds.y, bounds.width, bounds.height, 7);
                    text(graphics, "Назначить", bounds.x + 10, bounds.y + 17, 9, Font.BOLD, TEXT);
                }
                case ACTION -> {
                    bounds = new Rectangle(x, y - 2, width, 28);
                    rounded(graphics, accentAlpha(24), bounds.x, bounds.y, bounds.width, bounds.height, 7);
                    graphics.setColor(accentAlpha(75));
                    graphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 7, 7);
                    text(graphics, hudManager.isManualEditMode() ? "FINISH HUD EDIT" : "START HUD EDIT",
                            bounds.x + 10, bounds.y + 18, 9, Font.BOLD, accent);
                }
                default -> throw new IllegalStateException("Unknown setting type");
            }
            settingHits.add(new SettingHit(bounds, module, setting));
        }

        private void drawConfigs(Graphics2D graphics, int width, int height) {
            int gap = 12;
            int cardWidth = Math.max(150, Math.min(220, (width - 250 - gap * 2) / 3));
            String[] descriptions = {"Стандартный профиль", "Для PvP-сессии", "Серверные настройки"};
            int index = 0;
            for (Map.Entry<String, OverlayPreferences.Profile> entry : configs.entrySet()) {
                int x = 210 + index % 3 * (cardWidth + gap);
                int y = 178 + index / 3 * 142;
                rounded(graphics, surfaceOverlay(8), x, y, cardWidth, 126, 13);
                graphics.setColor(entry.getKey().equals(activeConfig) ? accentAlpha(90) : surfaceOverlay(22));
                graphics.drawRoundRect(x, y, cardWidth, 126, 13, 13);
                text(graphics, entry.getKey(), x + 14, y + 27, 14, Font.BOLD, TEXT);
                text(graphics, index < descriptions.length ? descriptions[index] : "Сохранённый профиль", x + 14, y + 48, 9, Font.PLAIN, MUTED);
                text(graphics, entry.getKey().equals(activeConfig) ? "ACTIVE" : "LOCAL CONFIG", x + 14, y + 70, 8, Font.BOLD, entry.getKey().equals(activeConfig) ? accent : MUTED);
                int loadX = x + 13, buttonY = y + 84;
                rounded(graphics, accentAlpha(35), loadX, buttonY, 70, 27, 8);
                text(graphics, "Load", loadX + 22, buttonY + 18, 9, Font.BOLD, currentTheme().accent());
                actionHits.add(new ActionHit(new Rectangle(loadX, buttonY, 70, 27), entry.getKey(), "load"));
                int deleteX = x + 91;
                rounded(graphics, surfaceOverlay(9), deleteX, buttonY, 70, 27, 8);
                text(graphics, "Delete", deleteX + 17, buttonY + 18, 9, Font.BOLD, MUTED);
                actionHits.add(new ActionHit(new Rectangle(deleteX, buttonY, 70, 27), entry.getKey(), "delete"));
                index++;
            }
            int formY = Math.min(height - 95, 178 + ((index + 2) / 3) * 142 + 8);
            rounded(graphics, surfaceOverlay(7), 210, formY, Math.min(620, width - 244), 56, 11);
            Rectangle nameBounds = new Rectangle(222, formY + 12, 270, 32);
            rounded(graphics, configNameFocused ? (currentTheme().windowEnd()) : surfaceOverlay(8), nameBounds.x, nameBounds.y, nameBounds.width, nameBounds.height, 8);
            graphics.setColor(configNameFocused ? accentAlpha(100) : surfaceOverlay(18));
            graphics.drawRoundRect(nameBounds.x, nameBounds.y, nameBounds.width, nameBounds.height, 8, 8);
            text(graphics, configName.isEmpty() ? "Имя нового конфига" : configName + (configNameFocused ? "|" : ""), nameBounds.x + 10, nameBounds.y + 21, 9, Font.PLAIN, configName.isEmpty() ? MUTED : TEXT);
            Rectangle saveBounds = new Rectangle(503, formY + 12, 95, 32);
            rounded(graphics, accentAlpha(38), saveBounds.x, saveBounds.y, saveBounds.width, saveBounds.height, 8);
            drawPlus(graphics, saveBounds.x + 13, saveBounds.y + 11, currentTheme().accent());
            text(graphics, "Save", saveBounds.x + 31, saveBounds.y + 21, 10, Font.BOLD, currentTheme().accent());
            actionHits.add(new ActionHit(saveBounds, "", "save"));
        }

        private void drawBinds(Graphics2D graphics, int width, int height) {
            int columns = 3;
            int cardWidth = Math.max(150, (width - 254) / columns);
            int index = 0;
            for (ModuleDef module : MODULES) {
                int x = 210 + (index % columns) * (cardWidth + 10);
                int y = 178 + (index / columns) * 52;
                rounded(graphics, surfaceOverlay(7), x, y, cardWidth, 44, 9);
                text(graphics, module.id(), x + 11, y + 27, 10, Font.BOLD, TEXT);
                Rectangle keyBounds = new Rectangle(x + cardWidth - 83, y + 9, 72, 26);
                boolean waiting = module.id().equals(waitingBind);
                rounded(graphics, waiting ? accentAlpha(34) : surfaceOverlay(10), keyBounds.x, keyBounds.y, keyBounds.width, keyBounds.height, 7);
                text(graphics, waiting ? "PRESS KEY" : binds.getOrDefault(module.id(), "NONE"), keyBounds.x + 8, keyBounds.y + 17, 7, Font.BOLD, waiting ? accent : TEXT);
                actionHits.add(new ActionHit(keyBounds, module.id(), "bind"));
                index++;
            }
        }

        private void drawResizeGrip(Graphics2D graphics, int width, int height) {
            graphics.setColor(accentAlpha(120));
            graphics.drawLine(width - 18, height - 5, width - 5, height - 18);
            graphics.drawLine(width - 12, height - 5, width - 5, height - 12);
            graphics.drawLine(width - 6, height - 5, width - 5, height - 6);
        }

        private void drawSwitch(Graphics2D graphics, int x, int y, float progress) {
            float value = Math.max(0.0f, Math.min(1.0f, progress));
            rounded(graphics, blend(new Color(64, 62, 71), accent, value), x, y, 36, 20, 10);
            graphics.setColor(Color.WHITE);
            graphics.fillOval(x + 3 + Math.round(value * 17), y + 3, 14, 14);
        }

        private void drawWrapped(Graphics2D graphics, String value, int x, int y, int maxWidth, int size, Color color, int maxLines) {
            graphics.setFont(new Font("Dialog", Font.PLAIN, size)); graphics.setColor(color);
            FontMetrics metrics = graphics.getFontMetrics();
            StringBuilder line = new StringBuilder(); int lineY = y; int lines = 0;
            for (String word : value.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (metrics.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                    graphics.drawString(line.toString(), x, lineY); line.setLength(0); line.append(word); lineY += 14;
                    if (++lines >= maxLines) return;
                } else {
                    if (!line.isEmpty()) line.append(' ');
                    line.append(word);
                }
            }
            if (!line.isEmpty()) graphics.drawString(line.toString(), x, lineY);
        }

        private boolean inside(int x, int y, int width, int height) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        private void selectTab(String tab) {
            if (selectedTab.equals(tab)) return;
            selectedTab = tab;
            selectedModule = null;
            search = "";
            searchFocused = false;
            configNameFocused = false;
            persist();
            if (contentAnimationTimer != null) contentAnimationTimer.stop();
            contentAnimationProgress = 0.0f;
            long started = System.nanoTime();
            contentAnimationTimer = new Timer(16, event -> {
                float time = Math.min(1.0f, (System.nanoTime() - started) / 320_000_000.0f);
                contentAnimationProgress = smoothStep(time);
                repaint();
                if (time >= 1.0f) {
                    contentAnimationTimer.stop();
                    contentAnimationTimer = null;
                    contentAnimationProgress = 1.0f;
                }
            });
            contentAnimationTimer.start();
            repaint();
        }

        private void handleClick(MouseEvent event) {
            int x = event.getX(), y = event.getY();
            if (event.getButton() == MouseEvent.BUTTON1 && x >= getWidth() - 22 && y >= getHeight() - 22) {
                resizing = true; return;
            }
            if (closing || animationProgress < 0.999f || contentAnimationProgress < 0.999f) return;
            if (x >= getWidth() - 60 && y < TOP_BAR) { closeAction.run(); return; }
            int appearanceY = getHeight() - 86;
            if (x >= 28 && x <= 154 && y >= appearanceY + 27 && y <= appearanceY + 54) {
                setTheme("light".equals(themeId) ? "dark" : "light");
                repaint();
                return;
            }
            if (y >= 18 && y <= 58 && x >= 248 && x < getWidth() - 270) {
                searchFocused = true; configNameFocused = false; return;
            }
            if (y >= 18 && y <= 58 && x >= getWidth() - 212 && x < getWidth() - 70) {
                selectTab("configs"); return;
            }
            if (x >= 14 && x <= 174 && y >= 120 && y < 120 + TAB_IDS.length * 48) {
                int index = (y - 120) / 48;
                if (index >= 0 && index < TAB_IDS.length) {
                    selectTab(TAB_IDS[index]); return;
                }
            }
            for (ActionHit hit : actionHits) {
                if (!hit.bounds().contains(x, y)) continue;
                if (hit.action().equals("bind")) waitingBind = hit.config();
                else if (hit.action().equals("save")) saveNamedConfig();
                else if (hit.action().equals("load")) loadConfig(hit.config());
                else if (hit.action().equals("delete")) deleteConfig(hit.config());
                repaint(); return;
            }
            for (SettingHit hit : settingHits) {
                if (!hit.bounds().contains(x, y)) continue;
                String key = settingKey(hit.module(), hit.setting());
                switch (hit.setting().type()) {
                    case SLIDER -> { activeSlider = key; updateSlider(key, x); }
                    case DROPDOWN -> {
                        int next = ((Number) values.get(key)).intValue() + 1;
                        values.put(key, Math.floorMod(next, hit.setting().options().length));
                    }
                    case CHECKBOX -> values.put(key, ((Number) values.get(key)).doubleValue() == 0 ? 1.0 : 0.0);
                    case COLOR -> {
                        Color[] palette = palette(); int chosen = 0;
                        for (int i = 0; i < palette.length; i++) if (palette[i].equals(values.get(key))) chosen = i;
                        values.put(key, palette[(chosen + 1) % palette.length]);
                    }
                    case BIND -> waitingBind = hit.module().id();
                    case ACTION -> hudManager.toggleManualEditMode();
                }
                applySupportedFeatures();
                persist();
                repaint(); return;
            }
            for (ModuleHit hit : moduleHits) {
                if (!hit.bounds().contains(x, y)) continue;
                boolean clickedGear = x >= hit.bounds().x + hit.bounds().width - 40
                        && y >= hit.bounds().y + 52 && y <= hit.bounds().y + 82;
                if (event.getButton() == MouseEvent.BUTTON3 || clickedGear) {
                    selectedModule = selectedModule != null && selectedModule.equals(hit.id()) ? null : hit.id();
                } else {
                    NativeOverlay.this.setEnabled(hit.id(), !enabled.getOrDefault(hit.id(), false));
                }
                repaint(); return;
            }
            if (selectedTab.equals("configs") && y > 220 && x >= 210 && x < 500) {
                configNameFocused = true; searchFocused = false; return;
            }
            searchFocused = false; configNameFocused = false;
        }

        private void updateSlider(String key, int mouseX) {
            for (SettingHit hit : settingHits) {
                if (!settingKey(hit.module(), hit.setting()).equals(key)) continue;
                Rectangle bounds = hit.bounds();
                double ratio = Math.max(0, Math.min(1, (mouseX - bounds.x) / (double) bounds.width));
                double value = hit.setting().min() + ratio * (hit.setting().max() - hit.setting().min());
                values.put(key, key.equals("AutoTotem.Delay") ? Math.round(value) : Math.round(value * 10.0) / 10.0);
                applySupportedFeatures();
                repaint(); return;
            }
        }

        private void saveNamedConfig() {
            String name = configName.trim();
            if (name.isEmpty()) name = "config" + (configs.size() + 1);
            configs.put(name, snapshotProfile());
            activeConfig = name; configName = ""; configNameFocused = false; persist();
        }

        private void loadConfig(String name) {
            OverlayPreferences.Profile profile = configs.get(name);
            if (profile == null) return;
            for (ModuleDef module : MODULES) {
                boolean defaultEnabled = module.id().equals("HUD") || module.id().equals("Coordinates");
                boolean wasEnabled = enabled.getOrDefault(module.id(), defaultEnabled);
                boolean willBeEnabled = profile.enabled().getOrDefault(module.id(), defaultEnabled);
                enabled.put(module.id(), willBeEnabled);
                if (wasEnabled != willBeEnabled) animateModuleToggle(module.id(), willBeEnabled);
            }
            for (ModuleDef module : MODULES) {
                for (SettingDef setting : module.settings()) {
                    String key = settingKey(module, setting);
                    values.put(key, readSetting(setting, profile.settings().get(key), values.get(key)));
                }
            }
            profile.binds().forEach(binds::put);
            activeConfig = name; applySupportedFeatures(); persist();
        }

        private void deleteConfig(String name) {
            if (configs.size() <= 1 || name.equals("default")) return;
            configs.remove(name);
            if (activeConfig.equals(name)) loadConfig("default");
            else persist();
        }

        private int indexOfTab(String tab) {
            for (int index = 0; index < TAB_IDS.length; index++) if (TAB_IDS[index].equals(tab)) return index;
            return 0;
        }
    }

    private static Color[] palette() {
        return new Color[]{new Color(183, 121, 255), new Color(93, 164, 255), new Color(78, 226, 166),
                new Color(255, 117, 164), new Color(255, 192, 88), Color.WHITE};
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

}
