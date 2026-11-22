package dev.ecpis.ecpisdlc;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;
import java.util.Locale;

public final class TargetHUD extends HudElement {
    private static final long DISPLAY_MILLIS = 3500L;
    private static final long FADE_MILLIS = 300L;
    private volatile TargetFrame frame = TargetFrame.hidden();
    private LivingEntity target;
    private Player attacker;
    private long lastAttackTime;

    public TargetHUD() {
        super("TargetHUD", 24, 176, 236, 84, true);
    }

    public void onAttack(Player attacker, Entity attacked) {
        if (!(attacked instanceof LivingEntity living) || attacked == attacker) return;
        if (!living.isAlive() || living.getHealth() <= 0.0f) {
            clearTarget();
            return;
        }
        this.attacker = attacker;
        target = living;
        lastAttackTime = System.currentTimeMillis();
        frame = TargetFrame.hidden();
    }

    public void clearTarget() {
        target = null;
        attacker = null;
        lastAttackTime = 0L;
        frame = TargetFrame.hidden();
    }

    @Override
    public void onTick() {
        LivingEntity currentTarget = target;
        Player currentAttacker = attacker;
        if (currentTarget == null || currentAttacker == null) {
            frame = TargetFrame.hidden();
            return;
        }
        if (!currentTarget.isAlive() || currentTarget.getHealth() <= 0.0f) {
            clearTarget();
            return;
        }

        long elapsed = Math.max(0L, System.currentTimeMillis() - lastAttackTime);
        float alpha = elapsed <= DISPLAY_MILLIS ? 1.0f : 1.0f - (elapsed - DISPLAY_MILLIS) / (float) FADE_MILLIS;
        if (alpha <= 0.0f) {
            clearTarget();
            return;
        }
        float maxHealth = Math.max(1.0f, currentTarget.getMaxHealth());
        float health = Math.max(0.0f, Math.min(maxHealth, currentTarget.getHealth()));
        float healthPercent = Math.max(0.0f, Math.min(1.0f, health / maxHealth));
        float distance = currentAttacker.distanceTo(currentTarget);
        frame = new TargetFrame(
                true,
                currentTarget.getDisplayName().getString(),
                String.format(Locale.ROOT, "%.1f / %.1f", health, maxHealth),
                String.format(Locale.ROOT, "%.1fm", distance),
                healthPercent,
                alpha
        );
    }

    @Override
    public void render(EcpisRenderer renderer) {
        TargetFrame current = frame;
        if (!current.visible()) {
            if (isEditing()) drawEditorPlaceholder(renderer);
            return;
        }
        float alpha = current.alpha();
        renderer.drawRoundRect(x, y, width, height, 12, EcpisRenderer.withAlpha(renderer.surfaceColor(), alpha));
        renderer.drawRoundRectOutline(x, y, width, height, 12, EcpisRenderer.withAlpha(renderer.borderColor(), alpha), 1.0f);
        renderer.drawText(current.name(), x + 13, y + 22, EcpisRenderer.withAlpha(renderer.textColor(), alpha), 1.0f);
        renderer.drawText(current.healthText(), x + 13, y + 43, EcpisRenderer.withAlpha(renderer.mutedColor(), alpha), 0.82f);
        renderer.drawText(current.distanceText(), x + width - renderer.getTextWidth(current.distanceText(), 0.82f) - 13,
                y + 43, EcpisRenderer.withAlpha(renderer.accentColor(), alpha), 0.82f);
        renderer.drawRoundRect(x + 13, y + 57, width - 26, 8, 4, EcpisRenderer.withAlpha(new Color(115, 128, 137), alpha * 0.32f));
        int fillWidth = Math.round((width - 26) * current.healthPercent());
        renderer.drawRoundRect(x + 13, y + 57, fillWidth, 8, 4,
                EcpisRenderer.withAlpha(healthColor(current.healthPercent()), alpha));
    }

    private static Color healthColor(float percent) {
        float amount = Math.max(0.0f, Math.min(1.0f, percent));
        Color red = new Color(244, 83, 91);
        Color green = new Color(73, 211, 139);
        return new Color(
                Math.round(red.getRed() + (green.getRed() - red.getRed()) * amount),
                Math.round(red.getGreen() + (green.getGreen() - red.getGreen()) * amount),
                Math.round(red.getBlue() + (green.getBlue() - red.getBlue()) * amount)
        );
    }

    private record TargetFrame(boolean visible, String name, String healthText, String distanceText,
                               float healthPercent, float alpha) {
        private static TargetFrame hidden() {
            return new TargetFrame(false, "", "", "", 0.0f, 0.0f);
        }
    }
}
