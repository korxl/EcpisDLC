package dev.ecpis.ecpisdlc;

public final class HudModule extends Module {
    private final HudManager manager;

    HudModule(HudManager manager) {
        super("HUD");
        this.manager = manager;
    }

    public void setFeatureEnabled(String feature, boolean enabled) {
        manager.setElementEnabled(feature, enabled);
    }

    public boolean isFeatureEnabled(String feature) {
        return manager.isElementEnabled(feature);
    }

    public void requestEditMode() {
        manager.setManualEditMode(true);
    }

    public void toggleEditMode() {
        manager.toggleManualEditMode();
    }
}
