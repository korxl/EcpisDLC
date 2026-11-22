package dev.ecpis.ecpisdlc;

import java.util.List;

public final class PotionHUD extends HudElement {
    private volatile List<String> effects = List.of();

    public PotionHUD() {
        super("PotionHUD", 24, 24, 244, 56, true);
    }

    void setEffects(List<String> effects) {
        this.effects = List.copyOf(effects);
    }

    @Override
    public void onTick() {
        setSize(width, Math.max(56, Math.min(196, 38 + effects.size() * 19)));
    }

    @Override
    public void render(EcpisRenderer renderer) {
        List<String> currentEffects = effects;
        if (currentEffects.isEmpty() && !isEditing()) return;
        if (currentEffects.isEmpty()) {
            drawEditorPlaceholder(renderer);
            return;
        }
        drawPanel(renderer);
        renderer.drawText("ACTIVE EFFECTS", x + 12, y + 19, renderer.accentColor(), 0.72f);
        renderer.pushClip(x + 10, y + 25, width - 20, height - 30);
        int row = 0;
        for (String effect : currentEffects) {
            if (row >= 8) break;
            renderer.drawText(effect, x + 12, y + 42 + row * 18, renderer.textColor(), 0.78f);
            row++;
        }
        renderer.popClip();
    }
}
