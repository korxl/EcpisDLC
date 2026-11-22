package dev.ecpis.ecpisdlc;

public final class Notification {
    public static final long DISPLAY_MILLIS = 3000L;
    private final String message;
    private final long createdAt;

    public Notification(String message) {
        this(message, System.currentTimeMillis());
    }

    Notification(String message, long createdAt) {
        this.message = message;
        this.createdAt = createdAt;
    }

    public String message() { return message; }
    public long createdAt() { return createdAt; }
    public boolean isExpired(long now) { return now - createdAt >= DISPLAY_MILLIS; }

    public float alpha(long now) {
        long age = Math.max(0L, now - createdAt);
        long fadeStart = DISPLAY_MILLIS - 250L;
        return age <= fadeStart ? 1.0f : Math.max(0.0f, 1.0f - (age - fadeStart) / 250.0f);
    }
}
