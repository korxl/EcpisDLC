package dev.ecpis.ecpisdlc;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class EcpisDLCClient implements ClientModInitializer {
    private static final NativeOverlay OVERLAY = new NativeOverlay();
    private static final ClientFeatures FEATURES = new ClientFeatures(OVERLAY);
    private static final OverlayController OVERLAY_CONTROLLER = new OverlayController(OVERLAY);
    private static KeyMapping openGuiKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("ecpisdlc", "client")
        );
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ecpisdlc.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                OVERLAY_CONTROLLER.toggle(client);
            }
            OVERLAY_CONTROLLER.tick(client);
            FEATURES.tick(client);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            FEATURES.shutdown(client);
            OVERLAY.dispose();
        });
    }

    static void closeOverlayFromWindow() {
        Minecraft client = Minecraft.getInstance();
        OVERLAY_CONTROLLER.close(client);
    }
}
