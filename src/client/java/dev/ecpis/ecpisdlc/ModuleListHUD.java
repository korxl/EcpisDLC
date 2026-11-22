package dev.ecpis.ecpisdlc;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class ModuleListHUD extends HudElement {
    private final LinkedHashSet<Module> enabledModules = new LinkedHashSet<>();
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleListHUD() {
        super("ModuleList", 930, 24, 220, 36, true);
    }

    public synchronized void setModuleEnabled(String name, boolean enabled) {
        Module module = modules.computeIfAbsent(name, key -> new Module(key) { });
        module.setEnabled(enabled);
        if (enabled) enabledModules.add(module);
        else enabledModules.remove(module);
        setSize(Math.max(150, width), Math.max(36, enabledModules.size() * 19 + 24));
    }

    @Override
    public void onTick() {
        synchronized (this) {
            setSize(Math.max(150, width), Math.max(36, enabledModules.size() * 19 + 24));
        }
    }

    @Override
    public synchronized void render(EcpisRenderer renderer) {
        if (enabledModules.isEmpty() && !isEditing()) return;
        if (enabledModules.isEmpty()) {
            drawEditorPlaceholder(renderer);
            return;
        }
        drawPanel(renderer);
        Iterator<Module> iterator = enabledModules.stream()
                .sorted(Comparator.comparingInt((Module module) -> module.name().length()).reversed()
                        .thenComparing(Module::name))
                .iterator();
        int row = 0;
        while (iterator.hasNext()) {
            Module module = iterator.next();
            String label = module.name();
            int textWidth = renderer.getTextWidth(label, 0.82f);
            int labelX = x + width - 12 - textWidth;
            renderer.drawText(label, labelX, y + 20 + row * 19, renderer.textColor(), 0.82f);
            renderer.drawRoundRect(x + width - 7, y + 12 + row * 19, 3, 3, 2, renderer.accentColor());
            row++;
        }
    }
}
