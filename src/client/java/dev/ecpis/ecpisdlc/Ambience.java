package dev.ecpis.ecpisdlc;

public final class Ambience extends Module {
    public static final Ambience INSTANCE = new Ambience();

    private volatile int timeMode;
    private volatile int weatherMode;
    private volatile int fogMode;
    private volatile int fogColor = 0xFFB779FF;
    private volatile boolean clouds = true;
    private volatile int cloudColor = 0xFFFFFFFF;
    private volatile int lighting;

    private Ambience() {
        super("Ambience");
    }

    public void configure(int timeMode, int weatherMode, int fogMode, int fogColor,
                          boolean clouds, int cloudColor, int lighting) {
        this.timeMode = clamp(timeMode, 0, 4);
        this.weatherMode = clamp(weatherMode, 0, 3);
        this.fogMode = clamp(fogMode, 0, 2);
        this.fogColor = fogColor | 0xFF000000;
        this.clouds = clouds;
        this.cloudColor = cloudColor | 0xFF000000;
        this.lighting = clamp(lighting, 0, 100);
    }

    public int timeMode() {
        return timeMode;
    }

    public int weatherMode() {
        return weatherMode;
    }

    public int fogMode() {
        return fogMode;
    }

    public int fogColor() {
        return fogColor;
    }

    public boolean clouds() {
        return clouds;
    }

    public int cloudColor() {
        return cloudColor;
    }

    public int lighting() {
        return lighting;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
