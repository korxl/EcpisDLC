package dev.ecpis.ecpisdlc;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.JWindow;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.atomic.AtomicBoolean;

final class NativeOverlay {
    private static final int TABLET_WIDTH = 620;
    private static final int TABLET_HEIGHT = 440;
    private static final Color TEXT = new Color(239, 241, 249);
    private static final Color MUTED = new Color(139, 146, 164);
    private static final Color ACCENT = new Color(151, 115, 255);

    private final AtomicBoolean updatePending = new AtomicBoolean();
    private volatile int gameX;
    private volatile int gameY;
    private volatile int gameWidth = 1280;
    private volatile int gameHeight = 720;
    private volatile boolean tabletVisible;
    private volatile boolean hudEnabled = true;
    private volatile boolean focusRequested;
    private volatile boolean disposed;
    private volatile HudSnapshot snapshot = HudSnapshot.disconnected();
    private volatile Runnable closeAction = () -> { };

    private JWindow tabletWindow;
    private JWindow hudWindow;
    private TabletPanel tabletPanel;
    private HudPanel hudPanel;

    record HudSnapshot(boolean connected, String playerName, String health, String totems, String coordinates) {
        static HudSnapshot disconnected() {
            return new HudSnapshot(false, "Игрок", "--", "0", "---  ---  ---");
        }
    }

    void show(int x, int y, int width, int height, Runnable closeAction) {
        this.closeAction = closeAction;
        this.tabletVisible = true;
        this.focusRequested = true;
        updateWindowBounds(x, y, width, height);
    }

    void hideTablet() {
        tabletVisible = false;
        scheduleUpdate();
    }

    void updateHud(HudSnapshot snapshot, boolean enabled) {
        this.snapshot = snapshot;
        this.hudEnabled = enabled;
        scheduleUpdate();
    }

    void updateWindowBounds(int x, int y, int width, int height) {
        gameX = x;
        gameY = y;
        if (width > 0) {
            gameWidth = width;
        }
        if (height > 0) {
            gameHeight = height;
        }
        scheduleUpdate();
    }

    private void scheduleUpdate() {
        if (disposed || !updatePending.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            updatePending.set(false);
            if (disposed) {
                return;
            }
            ensureWindows();
            applyWindowState();
        });
    }

    private void ensureWindows() {
        if (tabletWindow != null) {
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // The overlay is custom-painted and does not depend on a Swing look and feel.
        }

        tabletPanel = new TabletPanel();
        tabletPanel.setPreferredSize(new Dimension(TABLET_WIDTH, TABLET_HEIGHT));
        tabletWindow = new JWindow();
        configureWindow(tabletWindow, true);
        tabletWindow.setContentPane(tabletPanel);
        tabletWindow.pack();

        hudPanel = new HudPanel();
        hudPanel.setPreferredSize(new Dimension(350, 116));
        hudWindow = new JWindow();
        configureWindow(hudWindow, false);
        hudWindow.setContentPane(hudPanel);
        hudWindow.pack();
    }

    private static void configureWindow(JWindow window, boolean focusable) {
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(focusable);
        try {
            window.setBackground(new Color(0, 0, 0, 0));
        } catch (UnsupportedOperationException ignored) {
            window.setBackground(new Color(12, 14, 22));
        }
    }

    private void applyWindowState() {
        if (tabletWindow == null || hudWindow == null) {
            return;
        }

        tabletWindow.setLocation(
                gameX + (gameWidth - TABLET_WIDTH) / 2,
                gameY + (gameHeight - TABLET_HEIGHT) / 2
        );
        hudWindow.setLocation(gameX + 18, gameY + 18);
        tabletPanel.repaint();
        hudPanel.setSnapshot(snapshot);
        hudPanel.repaint();

        if (tabletVisible) {
            if (!tabletWindow.isVisible()) {
                tabletWindow.setVisible(true);
            }
            if (focusRequested) {
                focusRequested = false;
                tabletWindow.toFront();
                tabletPanel.requestFocusInWindow();
            }
        } else if (tabletWindow.isVisible()) {
            tabletWindow.setVisible(false);
        }

        boolean showHud = hudEnabled && snapshot.connected() && !tabletVisible;
        if (showHud && !hudWindow.isVisible()) {
            hudWindow.setVisible(true);
        } else if (!showHud && hudWindow.isVisible()) {
            hudWindow.setVisible(false);
        }
    }

    void dispose() {
        disposed = true;
        SwingUtilities.invokeLater(() -> {
            if (tabletWindow != null) {
                tabletWindow.dispose();
                tabletWindow = null;
            }
            if (hudWindow != null) {
                hudWindow.dispose();
                hudWindow = null;
            }
            tabletPanel = null;
            hudPanel = null;
        });
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

    private final class TabletPanel extends JPanel {
        private int selectedTab;
        private int mouseX = -1;
        private int mouseY = -1;

        TabletPanel() {
            setOpaque(false);
            setFocusable(true);
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent event) {
                    mouseX = event.getX();
                    mouseY = event.getY();
                    repaint();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    handleClick(event.getX(), event.getY());
                }
            });
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent event) {
                    boolean rightShift = event.getKeyCode() == KeyEvent.VK_SHIFT
                            && event.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT;
                    if (event.getKeyCode() == KeyEvent.VK_ESCAPE || rightShift) {
                        closeAction.run();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics source) {
            super.paintComponent(source);
            Graphics2D graphics = (Graphics2D) source.create();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            for (int spread = 5; spread > 0; spread--) {
                graphics.setColor(new Color(0, 0, 0, 8 + spread * 2));
                graphics.fillRoundRect(spread / 2, spread / 2, TABLET_WIDTH - spread, TABLET_HEIGHT - spread, 32, 32);
            }
            graphics.setPaint(new GradientPaint(10, 10, new Color(26, 28, 40), 610, 430, new Color(15, 17, 26)));
            graphics.fillRoundRect(0, 0, TABLET_WIDTH - 1, TABLET_HEIGHT - 1, 30, 30);
            graphics.setColor(new Color(255, 255, 255, 22));
            graphics.drawRoundRect(0, 0, TABLET_WIDTH - 1, TABLET_HEIGHT - 1, 30, 30);

            graphics.setClip(new RoundRectangle2D.Double(0, 0, TABLET_WIDTH, TABLET_HEIGHT, 30, 30));
            graphics.setPaint(new GradientPaint(10, 10, new Color(23, 25, 37), 168, 428, new Color(17, 19, 29)));
            graphics.fillRoundRect(0, 0, 170, TABLET_HEIGHT, 30, 30);
            graphics.fillRect(140, 0, 38, TABLET_HEIGHT);
            graphics.setClip(null);
            graphics.setColor(new Color(255, 255, 255, 17));
            graphics.drawLine(178, 24, 178, 412);

            drawSidebar(graphics);
            drawContent(graphics);
            graphics.dispose();
        }

        private void drawSidebar(Graphics2D graphics) {
            graphics.setPaint(new GradientPaint(27, 33, new Color(183, 137, 255), 60, 68, new Color(116, 91, 239)));
            graphics.fillRoundRect(25, 28, 38, 38, 13, 13);
            text(graphics, "E", 37, 55, 23, Font.BOLD, Color.WHITE);
            text(graphics, "EcpisDLC", 72, 44, 15, Font.BOLD, TEXT);
            text(graphics, "CLIENT MOD", 73, 61, 9, Font.BOLD, MUTED);
            graphics.setColor(new Color(255, 255, 255, 20));
            graphics.drawLine(25, 86, 158, 86);
            text(graphics, "РАЗДЕЛЫ", 27, 111, 9, Font.BOLD, MUTED);

            drawNavigationItem(graphics, 0, "Бой", "A");
            drawNavigationItem(graphics, 1, "Визуал", "V");
            drawNavigationItem(graphics, 2, "HUD", "H");

            rounded(graphics, new Color(255, 255, 255, 8), 24, 365, 134, 43, 12);
            graphics.setColor(new Color(101, 220, 169));
            graphics.fillOval(35, 379, 8, 8);
            text(graphics, "FABRIC 1.21.11", 50, 387, 10, Font.BOLD, new Color(214, 219, 232));
            text(graphics, "Java overlay enabled", 35, 400, 9, Font.PLAIN, MUTED);
        }

        private void drawNavigationItem(Graphics2D graphics, int index, String label, String icon) {
            int y = 127 + index * 47;
            boolean active = selectedTab == index;
            if (active) {
                rounded(graphics, new Color(151, 115, 255, 35), 20, y, 142, 38, 11);
                rounded(graphics, ACCENT, 20, y + 8, 3, 22, 3);
            } else if (inside(20, y, 142, 38)) {
                rounded(graphics, new Color(255, 255, 255, 10), 20, y, 142, 38, 11);
            }
            text(graphics, icon, 34, y + 25, 12, Font.BOLD, active ? new Color(203, 183, 255) : MUTED);
            text(graphics, label, 58, y + 25, 12, active ? Font.BOLD : Font.PLAIN, active ? TEXT : new Color(176, 181, 196));
        }

        private void drawContent(Graphics2D graphics) {
            text(graphics, "ПАНЕЛЬ УПРАВЛЕНИЯ", 198, 38, 9, Font.BOLD, MUTED);
            String title = switch (selectedTab) {
                case 0 -> "Боевые модули";
                case 1 -> "Визуальные модули";
                default -> "Экранный HUD";
            };
            text(graphics, title, 196, 66, 21, Font.BOLD, TEXT);
            text(graphics, "Нажми на карточку, чтобы переключить функцию", 198, 87, 10, Font.PLAIN, MUTED);

            rounded(graphics, new Color(101, 220, 169, 22), 516, 25, 53, 22, 11);
            graphics.setColor(new Color(101, 220, 169));
            graphics.fillOval(526, 32, 7, 7);
            text(graphics, "LIVE", 538, 41, 9, Font.BOLD, new Color(160, 238, 202));
            rounded(graphics, new Color(255, 255, 255, 9), 577, 23, 28, 26, 9);
            text(graphics, "×", 585, 42, 18, Font.PLAIN, new Color(199, 202, 215));

            switch (selectedTab) {
                case 0 -> {
                    drawFeatureCard(graphics, 192, 116, 194, 132, "AutoTotem", "Меняет тотем в левую руку", "AT", FeatureState.autoTotem);
                    drawInfoCard(graphics, 398, 116, "БЫСТРЫЙ СВАП", "Работает через слоты инвентаря", "Не активируется при открытом контейнере.");
                }
                case 1 -> {
                    drawFeatureCard(graphics, 192, 116, 194, 132, "Fullbright", "Максимальная гамма игры", "FB", FeatureState.fullbright);
                    drawInfoCard(graphics, 398, 116, "БЕЗ X-RAY", "Только яркость", "Скрытые блоки и сущности не показываются.");
                }
                default -> {
                    drawFeatureCard(graphics, 192, 116, 194, 132, "HUD игрока", "Статус-панель вне игры", "HUD", FeatureState.hud);
                    drawFeatureCard(graphics, 398, 116, 194, 132, "Координаты", "XYZ в отдельном HUD", "XYZ", FeatureState.coordinates);
                }
            }

            rounded(graphics, new Color(255, 255, 255, 6), 192, 270, 400, 102, 16);
            text(graphics, "ECPIS / OVERLAY", 209, 295, 9, Font.BOLD, new Color(184, 162, 255));
            text(graphics, "Интерфейс рисуется Java2D в отдельном окне ОС.", 209, 320, 12, Font.PLAIN, TEXT);
            text(graphics, "Minecraft GUI renderer для планшета и HUD не используется.", 209, 342, 10, Font.PLAIN, MUTED);
            text(graphics, "RIGHT SHIFT  ·  ESC закрыть", 209, 390, 9, Font.BOLD, new Color(149, 155, 173));
            text(graphics, "v1.0.0", 554, 390, 9, Font.PLAIN, MUTED);
        }

        private void drawFeatureCard(Graphics2D graphics, int x, int y, int width, int height,
                                     String title, String description, String icon, boolean enabled) {
            boolean hovered = inside(x, y, width, height);
            rounded(graphics, hovered ? new Color(39, 42, 58) : new Color(31, 34, 48), x, y, width, height, 16);
            graphics.setColor(new Color(255, 255, 255, hovered ? 28 : 17));
            graphics.drawRoundRect(x, y, width, height, 16, 16);
            graphics.setPaint(new GradientPaint(x + 15, y + 18, new Color(151, 115, 255, 80), x + 48, y + 53, new Color(93, 110, 244, 45)));
            graphics.fillRoundRect(x + 14, y + 15, 38, 38, 12, 12);
            text(graphics, icon, x + 21, y + 39, icon.length() > 2 ? 9 : 11, Font.BOLD, new Color(216, 204, 255));
            text(graphics, title, x + 14, y + 75, 13, Font.BOLD, TEXT);
            drawWrapped(graphics, description, x + 14, y + 93, width - 28, 11, MUTED);
            drawSwitch(graphics, x + width - 49, y + 17, enabled);
            text(graphics, enabled ? "ВКЛ" : "ВЫКЛ", x + width - 50, y + 119, 8, Font.BOLD,
                    enabled ? new Color(164, 234, 200) : MUTED);
        }

        private void drawInfoCard(Graphics2D graphics, int x, int y, String eyebrow, String title, String body) {
            rounded(graphics, new Color(31, 34, 48), x, y, 194, 132, 16);
            graphics.setColor(new Color(255, 255, 255, 17));
            graphics.drawRoundRect(x, y, 193, 131, 16, 16);
            text(graphics, eyebrow, x + 15, y + 27, 9, Font.BOLD, new Color(185, 166, 255));
            text(graphics, title, x + 15, y + 56, 13, Font.BOLD, TEXT);
            drawWrapped(graphics, body, x + 15, y + 79, 162, 11, MUTED);
            graphics.setColor(new Color(151, 115, 255, 180));
            graphics.fillRoundRect(x + 15, y + 111, 29, 3, 3, 3);
        }

        private void drawSwitch(Graphics2D graphics, int x, int y, boolean enabled) {
            rounded(graphics, enabled ? new Color(151, 115, 255) : new Color(66, 70, 86), x, y, 34, 19, 10);
            graphics.setColor(new Color(250, 250, 255));
            graphics.fillOval(enabled ? x + 18 : x + 3, y + 3, 13, 13);
        }

        private void drawWrapped(Graphics2D graphics, String value, int x, int y, int maxWidth, int fontSize, Color color) {
            graphics.setFont(new Font("Dialog", Font.PLAIN, fontSize));
            graphics.setColor(color);
            FontMetrics metrics = graphics.getFontMetrics();
            StringBuilder line = new StringBuilder();
            int lineY = y;
            for (String word : value.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (metrics.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                    graphics.drawString(line.toString(), x, lineY);
                    line.setLength(0);
                    line.append(word);
                    lineY += 15;
                } else {
                    if (!line.isEmpty()) {
                        line.append(' ');
                    }
                    line.append(word);
                }
            }
            if (!line.isEmpty()) {
                graphics.drawString(line.toString(), x, lineY);
            }
        }

        private boolean inside(int x, int y, int width, int height) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        private void handleClick(int x, int y) {
            if (x >= 577 && x <= 607 && y >= 20 && y <= 51) {
                closeAction.run();
                return;
            }
            if (x >= 20 && x <= 162) {
                for (int index = 0; index < 3; index++) {
                    int top = 127 + index * 47;
                    if (y >= top && y <= top + 38) {
                        selectedTab = index;
                        repaint();
                        return;
                    }
                }
            }

            if (selectedTab == 0 && x >= 192 && x <= 386 && y >= 116 && y <= 248) {
                FeatureState.autoTotem = !FeatureState.autoTotem;
            } else if (selectedTab == 1 && x >= 192 && x <= 386 && y >= 116 && y <= 248) {
                FeatureState.fullbright = !FeatureState.fullbright;
            } else if (selectedTab == 2 && y >= 116 && y <= 248) {
                if (x >= 192 && x <= 386) {
                    FeatureState.hud = !FeatureState.hud;
                } else if (x >= 398 && x <= 592) {
                    FeatureState.coordinates = !FeatureState.coordinates;
                }
            }
            repaint();
        }
    }

    private final class HudPanel extends JPanel {
        private HudSnapshot current = HudSnapshot.disconnected();

        HudPanel() {
            setOpaque(false);
        }

        void setSnapshot(HudSnapshot snapshot) {
            current = snapshot;
        }

        @Override
        protected void paintComponent(Graphics source) {
            super.paintComponent(source);
            Graphics2D graphics = (Graphics2D) source.create();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            for (int spread = 5; spread > 0; spread--) {
                graphics.setColor(new Color(0, 0, 0, 9));
                graphics.fillRoundRect(5 - spread / 2, 5 + spread / 2, 340 + spread, 106 + spread, 18, 18);
            }
            graphics.setPaint(new GradientPaint(7, 7, new Color(24, 26, 38, 242), 340, 110, new Color(15, 17, 27, 242)));
            graphics.fillRoundRect(7, 7, 338, 104, 17, 17);
            graphics.setColor(new Color(255, 255, 255, 27));
            graphics.drawRoundRect(7, 7, 337, 103, 17, 17);

            graphics.setColor(new Color(101, 220, 169));
            graphics.fillOval(21, 22, 8, 8);
            text(graphics, "ECPISDLC", 36, 30, 10, Font.BOLD, TEXT);
            text(graphics, current.playerName(), 125, 30, 9, Font.PLAIN, MUTED);
            graphics.setColor(new Color(255, 255, 255, 25));
            graphics.drawLine(20, 43, 332, 43);

            text(graphics, "ЗДОРОВЬЕ", 21, 61, 8, Font.BOLD, MUTED);
            text(graphics, current.health(), 21, 83, 16, Font.BOLD, new Color(245, 129, 153));
            text(graphics, "ТОТЕМЫ", 111, 61, 8, Font.BOLD, MUTED);
            text(graphics, current.totems(), 111, 83, 16, Font.BOLD, new Color(218, 203, 255));

            if (FeatureState.coordinates) {
                text(graphics, "XYZ", 176, 61, 8, Font.BOLD, MUTED);
                text(graphics, current.coordinates(), 176, 82, 9, Font.BOLD, new Color(190, 197, 216));
            } else {
                text(graphics, "HUD ACTIVE", 176, 61, 8, Font.BOLD, MUTED);
                text(graphics, "1.21.11", 176, 82, 9, Font.BOLD, new Color(190, 197, 216));
            }
            graphics.dispose();
        }
    }
}
