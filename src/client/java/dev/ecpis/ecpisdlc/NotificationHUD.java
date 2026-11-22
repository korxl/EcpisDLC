package dev.ecpis.ecpisdlc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public final class NotificationHUD extends HudElement {
    private final Deque<Notification> notifications = new ArrayDeque<>();

    public NotificationHUD() {
        super("Notifications", 900, 100, 260, 42, true);
    }

    public synchronized void push(String message) {
        if (message == null || message.isBlank()) return;
        notifications.addLast(new Notification(message));
        while (notifications.size() > 5) notifications.removeFirst();
        updateHeight();
    }

    @Override
    public synchronized void onTick() {
        long now = System.currentTimeMillis();
        while (!notifications.isEmpty() && notifications.peekFirst().isExpired(now)) {
            notifications.removeFirst();
        }
        updateHeight();
    }

    @Override
    public synchronized void render(EcpisRenderer renderer) {
        if (notifications.isEmpty() && !isEditing()) return;
        if (notifications.isEmpty()) {
            drawEditorPlaceholder(renderer);
            return;
        }
        renderer.pushClip(x, y, width, height);
        int row = 0;
        long now = System.currentTimeMillis();
        Iterator<Notification> iterator = notifications.descendingIterator();
        while (iterator.hasNext()) {
            Notification notification = iterator.next();
            int rowY = y + row * 48;
            float alpha = notification.alpha(now);
            renderer.drawRoundRect(x, rowY, width, 40, 10, EcpisRenderer.withAlpha(renderer.surfaceColor(), alpha));
            renderer.drawRoundRectOutline(x, rowY, width, 40, 10, EcpisRenderer.withAlpha(renderer.borderColor(), alpha), 1.0f);
            renderer.drawRoundRect(x + 10, rowY + 10, 3, 20, 2, EcpisRenderer.withAlpha(renderer.accentColor(), alpha));
            renderer.drawText(notification.message(), x + 21, rowY + 24,
                    EcpisRenderer.withAlpha(renderer.textColor(), alpha), 0.78f);
            row++;
        }
        renderer.popClip();
    }

    private void updateHeight() {
        setSize(width, Math.max(42, notifications.size() * 48 - 8));
    }
}
