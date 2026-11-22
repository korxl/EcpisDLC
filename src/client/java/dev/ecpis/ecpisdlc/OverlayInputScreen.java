package dev.ecpis.ecpisdlc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class OverlayInputScreen extends Screen {
    private final NativeOverlay overlay;

    OverlayInputScreen(NativeOverlay overlay) {
        super(Component.empty());
        this.overlay = overlay;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int scale = Minecraft.getInstance().getWindow().getGuiScale();
        overlay.drawTablet(graphics, width, height, scale);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        overlay.mouseClicked(event.x(), event.y(), event.button(), width, height,
                Minecraft.getInstance().getWindow().getGuiScale());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        overlay.mouseReleased(event.x(), event.y(), event.button(), width, height,
                Minecraft.getInstance().getWindow().getGuiScale());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        overlay.mouseDragged(event.x(), event.y(), event.button(), width, height,
                Minecraft.getInstance().getWindow().getGuiScale());
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        overlay.mouseMoved(mouseX, mouseY, width, height,
                Minecraft.getInstance().getWindow().getGuiScale());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        overlay.keyPressed(event.key(), event.scancode(), event.modifiers());
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        overlay.charTyped(event.codepoint(), event.modifiers());
        return true;
    }
}
