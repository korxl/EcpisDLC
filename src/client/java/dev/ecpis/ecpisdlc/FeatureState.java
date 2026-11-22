package dev.ecpis.ecpisdlc;

final class FeatureState {
    static volatile boolean fullbright;
    static volatile boolean autoTotem;
    static volatile boolean hud = true;
    static volatile boolean coordinates = true;
    static volatile boolean autoTool;
    static volatile int autoTotemDelayTicks = 10;
    static volatile double autoTotemHealthThreshold = 8.0;
    static volatile String autoTotemBind = "G";
    static volatile String autoToolBind = "T";
    static volatile String fullbrightBind = "N";
    static volatile String hudBind = "H";
    static volatile String coordinatesBind = "C";
    static volatile String ambienceBind = "M";

    private FeatureState() {
    }
}
