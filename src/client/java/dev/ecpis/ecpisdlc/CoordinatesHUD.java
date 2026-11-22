package dev.ecpis.ecpisdlc;

public final class CoordinatesHUD extends HudElement {
    private volatile String coordinates = "---  ---  ---";

    public CoordinatesHUD() {
        super("Coordinates", 24, 690, 210, 38, true);
    }

    void setCoordinates(String coordinates) {
        this.coordinates = coordinates;
    }

    @Override
    public void onTick() { }

    @Override
    public void render(EcpisRenderer renderer) {
        if (coordinates.startsWith("---") && !isEditing()) return;
        if (coordinates.startsWith("---")) {
            drawEditorPlaceholder(renderer);
            return;
        }
        drawPanel(renderer);
        renderer.drawText("POSITION", x + 12, y + 15, renderer.accentColor(), 0.62f);
        renderer.drawText(coordinates, x + 12, y + 31, renderer.textColor(), 0.78f);
    }
}
