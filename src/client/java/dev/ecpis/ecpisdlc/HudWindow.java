package dev.ecpis.ecpisdlc;

import javax.swing.JWindow;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

final class HudWindow {
    private final HudManager manager;
    private final JWindow window = new JWindow();
    private final HudPanel panel = new HudPanel();
    private boolean visible;

    HudWindow(HudManager manager) {
        this.manager = manager;
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        try {
            window.setBackground(new Color(0, 0, 0, 0));
        } catch (UnsupportedOperationException ignored) {
            window.setBackground(new Color(12, 14, 22));
        }
        panel.setOpaque(false);
        window.setContentPane(panel);
        window.setPreferredSize(new Dimension(1280, 720));
        window.pack();

        panel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    manager.beginDrag(event.getX(), event.getY());
                    refreshShape();
                }
            }

            @Override public void mouseReleased(MouseEvent event) {
                manager.endDrag();
                refreshShape();
            }
        });
        panel.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseDragged(MouseEvent event) {
                manager.dragTo(event.getX(), event.getY());
                refreshShape();
                panel.repaint();
            }
        });
    }

    void update(int x, int y, int width, int height, boolean lightTheme) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        manager.setViewport(safeWidth, safeHeight);
        window.setBounds(x, y, safeWidth, safeHeight);
        panel.setLightTheme(lightTheme);
        refreshShape();
        panel.repaint();
    }

    void setVisible(boolean visible) {
        if (this.visible == visible) return;
        this.visible = visible;
        window.setVisible(visible);
    }

    void dispose() {
        window.dispose();
    }

    private void refreshShape() {
        Shape shape = manager.windowShape(window.getWidth(), window.getHeight());
        try {
            window.setShape(shape);
        } catch (UnsupportedOperationException ignored) {
            window.setShape(null);
        }
    }

    private final class HudPanel extends JPanel {
        private volatile boolean lightTheme;

        void setLightTheme(boolean lightTheme) {
            this.lightTheme = lightTheme;
        }

        @Override protected void paintComponent(Graphics source) {
            super.paintComponent(source);
            Graphics2D graphics = (Graphics2D) source.create();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            manager.render(graphics, getWidth(), getHeight(), lightTheme);
            graphics.dispose();
        }
    }
}
