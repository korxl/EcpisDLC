package dev.ecpis.ecpisdlc;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

final class OverlayController {
    private final NativeOverlay overlay;
    private OverlayInputScreen inputScreen;
    private boolean open;

    OverlayController(NativeOverlay overlay) {
        this.overlay = overlay;
    }

    void toggle(Minecraft client) {
        if (open) {
            close(client);
        } else {
            open(client);
        }
    }

    void open(Minecraft client) {
        if (client.player == null || client.screen != null) {
            return;
        }

        Window window = client.getWindow();
        inputScreen = new OverlayInputScreen();
        open = true;
        client.setScreen(inputScreen);
        overlay.show(
                window.getX(),
                window.getY(),
                window.getScreenWidth(),
                window.getScreenHeight(),
                this::closeFromWindow
        );
    }

    void close(Minecraft client) {
        if (!open) {
            overlay.hideTablet();
            return;
        }

        open = false;
        overlay.hideTablet();
        long handle = client.getWindow().handle();
        if (client.screen == inputScreen) {
            GLFW.glfwFocusWindow(handle);
            client.setScreen(null);
        }
        inputScreen = null;
    }

    private void closeFromWindow() {
        Minecraft.getInstance().execute(() -> close(Minecraft.getInstance()));
    }

    void tick(Minecraft client) {
        if (open && client.screen != inputScreen) {
            open = false;
            inputScreen = null;
            overlay.hideTablet();
        }

        Window window = client.getWindow();
        overlay.updateWindowBounds(
                window.getX(),
                window.getY(),
                window.getScreenWidth(),
                window.getScreenHeight()
        );
    }
}
