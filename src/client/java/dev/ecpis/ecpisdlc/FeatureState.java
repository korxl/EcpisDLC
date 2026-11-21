package dev.ecpis.ecpisdlc;

final class FeatureState {
    static volatile boolean fullbright;
    static volatile boolean autoTotem;
    static volatile boolean hud = true;
    static volatile boolean coordinates = true;

    private FeatureState() {
    }
}
