package dev.ecpis.ecpisdlc;

import java.awt.Color;
import java.util.List;

public final class ArmorHUD extends HudElement {
    private volatile List<Durability> durability = List.of();

    public ArmorHUD() {
        super("ArmorHUD", 24, 520, 226, 142, true);
    }

    void setDurability(List<Durability> durability) {
        this.durability = List.copyOf(durability);
    }

    @Override
    public void onTick() { }

    @Override
    public void render(EcpisRenderer renderer) {
        List<Durability> current = durability;
        if (current.isEmpty() && !isEditing()) return;
        if (current.isEmpty()) {
            drawEditorPlaceholder(renderer);
            return;
        }
        drawPanel(renderer);
        renderer.drawText("EQUIPMENT", x + 12, y + 19, renderer.accentColor(), 0.72f);
        for (int index = 0; index < current.size(); index++) {
            Durability item = current.get(index);
            int rowY = y + 38 + index * 19;
            renderer.drawText(item.slot(), x + 12, rowY, renderer.mutedColor(), 0.65f);
            int barX = x + 62;
            int barWidth = 94;
            renderer.drawRoundRect(barX, rowY - 8, barWidth, 5, 3, EcpisRenderer.withAlpha(renderer.mutedColor(), 0.2f));
            if (item.maxDamage() > 0 && !item.empty()) {
                float remaining = Math.max(0.0f, Math.min(1.0f, 1.0f - item.damage() / (float) item.maxDamage()));
                renderer.drawRoundRect(barX, rowY - 8, Math.round(barWidth * remaining), 5, 3, durabilityColor(remaining));
                renderer.drawText(Integer.toString(Math.max(0, item.maxDamage() - item.damage())), x + 164, rowY,
                        renderer.textColor(), 0.67f);
            } else {
                renderer.drawText(item.empty() ? "EMPTY" : "N/A", x + 164, rowY, renderer.mutedColor(), 0.62f);
            }
        }
    }

    private static Color durabilityColor(float remaining) {
        float amount = Math.max(0.0f, Math.min(1.0f, remaining));
        Color red = new Color(244, 83, 91);
        Color green = new Color(73, 211, 139);
        return new Color(
                Math.round(red.getRed() + (green.getRed() - red.getRed()) * amount),
                Math.round(red.getGreen() + (green.getGreen() - red.getGreen()) * amount),
                Math.round(red.getBlue() + (green.getBlue() - red.getBlue()) * amount)
        );
    }

    static record Durability(String slot, int damage, int maxDamage, boolean empty) {
        static Durability empty(String slot) { return new Durability(slot, 0, 0, true); }
    }
}
