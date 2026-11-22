package dev.ecpis.ecpisdlc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/** AWT-backed renderer used exclusively by the native OS-window HUD. */
public final class EcpisRenderer {
    private final Graphics2D graphics;
    private final Deque<Optional<Shape>> clips = new ArrayDeque<>();
    private final Color textColor;
    private final Color mutedColor;
    private final Color surfaceColor;
    private final Color borderColor;
    private final Color accentColor;

    public EcpisRenderer(Graphics2D graphics, boolean lightTheme) {
        this.graphics = graphics;
        textColor = lightTheme ? new Color(30, 40, 49) : new Color(237, 243, 247);
        mutedColor = lightTheme ? new Color(93, 108, 120) : new Color(145, 160, 172);
        surfaceColor = lightTheme ? new Color(250, 252, 254, 242) : new Color(11, 14, 18, 238);
        borderColor = lightTheme ? new Color(91, 119, 142, 115) : new Color(154, 190, 215, 105);
        accentColor = lightTheme ? new Color(67, 105, 137) : new Color(133, 170, 204);
    }

    public void drawRect(int x, int y, int width, int height, Color color) {
        graphics.setColor(color);
        graphics.fillRect(x, y, Math.max(0, width), Math.max(0, height));
    }

    public void drawText(String text, int x, int y, Color color, float scale) {
        float safeScale = Math.max(0.5f, Math.min(3.0f, scale));
        graphics.setFont(new Font(Font.DIALOG, Font.PLAIN, Math.max(7, Math.round(12 * safeScale))));
        graphics.setColor(color);
        graphics.drawString(text, x, y);
    }

    public void drawRoundRect(int x, int y, int width, int height, int radius, Color color) {
        graphics.setColor(color);
        graphics.fill(new RoundRectangle2D.Float(x, y, Math.max(0, width), Math.max(0, height), radius * 2.0f, radius * 2.0f));
    }

    public void drawRoundRectOutline(int x, int y, int width, int height, int radius, Color color, float thickness) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(Math.max(0.5f, thickness)));
        graphics.draw(new RoundRectangle2D.Float(x, y, Math.max(0, width), Math.max(0, height), radius * 2.0f, radius * 2.0f));
    }

    public void drawLine(int x1, int y1, int x2, int y2, Color color, float thickness) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(Math.max(0.5f, thickness), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    public int getTextWidth(String text, float scale) {
        float safeScale = Math.max(0.5f, Math.min(3.0f, scale));
        Font previous = graphics.getFont();
        graphics.setFont(new Font(Font.DIALOG, Font.PLAIN, Math.max(7, Math.round(12 * safeScale))));
        FontMetrics metrics = graphics.getFontMetrics();
        int width = metrics.stringWidth(text);
        graphics.setFont(previous);
        return width;
    }

    public void pushClip(int x, int y, int width, int height) {
        clips.push(Optional.ofNullable(graphics.getClip()));
        graphics.clipRect(x, y, Math.max(0, width), Math.max(0, height));
    }

    public void popClip() {
        if (clips.isEmpty()) return;
        graphics.setClip(clips.pop().orElse(null));
    }

    public Color textColor() { return textColor; }
    public Color mutedColor() { return mutedColor; }
    public Color surfaceColor() { return surfaceColor; }
    public Color borderColor() { return borderColor; }
    public Color accentColor() { return accentColor; }

    public static Color withAlpha(Color color, float alpha) {
        int value = Math.max(0, Math.min(255, Math.round(color.getAlpha() * Math.max(0.0f, Math.min(1.0f, alpha)))));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), value);
    }
}
