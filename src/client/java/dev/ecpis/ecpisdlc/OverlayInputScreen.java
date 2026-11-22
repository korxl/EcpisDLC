package dev.ecpis.ecpisdlc;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class OverlayInputScreen extends Screen {
    OverlayInputScreen() {
        super(Component.empty());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally blank: the visible tablet is rendered by the native AWT window.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.input();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            EcpisDLCClient.closeOverlayFromWindow();
            return true;
        }
        return true;
    }
}
