package dev.ecpis.ecpisdlc;

public abstract class Module {
    private final String name;
    private volatile boolean enabled;

    protected Module(String name) {
        this.name = name;
    }

    public final String name() {
        return name;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
