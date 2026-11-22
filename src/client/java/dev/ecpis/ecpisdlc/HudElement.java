package dev.ecpis.ecpisdlc;

public abstract class HudElement {
    private final String id;
    protected volatile int x;
    protected volatile int y;
    protected volatile int width;
    protected volatile int height;
    private volatile boolean enabled;
    private volatile boolean editing;

    protected HudElement(String id, int x, int y, int width, int height, boolean enabled) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.enabled = enabled;
    }

    public final String id() { return id; }
    public final int x() { return x; }
    public final int y() { return y; }
    public final int width() { return width; }
    public final int height() { return height; }
    public final boolean isEnabled() { return enabled; }
    public final boolean isEditing() { return editing; }

    public final void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public final void setEnabled(boolean enabled) { this.enabled = enabled; }
    final void setEditing(boolean editing) { this.editing = editing; }

    protected final void setSize(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    public final boolean contains(int pointX, int pointY) {
        return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
    }

    protected final void drawPanel(EcpisRenderer renderer) {
        renderer.drawRoundRect(x, y, width, height, 12, renderer.surfaceColor());
        renderer.drawRoundRectOutline(x, y, width, height, 12, renderer.borderColor(), 1.0f);
    }

    protected final void drawEditorPlaceholder(EcpisRenderer renderer) {
        renderer.drawRoundRect(x, y, width, height, 12, EcpisRenderer.withAlpha(renderer.surfaceColor(), 0.76f));
        renderer.drawText(id, x + 10, y + Math.min(height - 6, 24), renderer.mutedColor(), 0.85f);
    }

    public abstract void render(EcpisRenderer renderer);
    public abstract void onTick();
}
