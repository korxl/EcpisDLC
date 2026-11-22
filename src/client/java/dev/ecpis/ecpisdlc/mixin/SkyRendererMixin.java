package dev.ecpis.ecpisdlc.mixin;

import dev.ecpis.ecpisdlc.Ambience;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
    @Inject(method = "method_74926", at = @At("TAIL"), remap = false)
    private void ecpisdlc$overrideVisualSky(ClientLevel world, float tickProgress, Camera camera,
                                            SkyRenderState state, CallbackInfo ci) {
        Ambience ambience = Ambience.INSTANCE;
        if (!ambience.isEnabled()) return;

        float time = selectedTime(ambience.timeMode());
        if (time >= 0.0f) {
            float celestial = time / 24000.0f - 0.25f;
            if (celestial < 0.0f) celestial++;
            if (celestial > 1.0f) celestial--;
            float eased = 1.0f - (float) ((Math.cos(celestial * Math.PI) + 1.0) / 2.0);
            float angle = (celestial + (eased - celestial) / 3.0f) * (float) (Math.PI * 2.0);
            state.sunAngle = angle;
            state.moonAngle = angle + (float) Math.PI;
            state.starAngle = angle;
        }

        state.rainBrightness = switch (ambience.weatherMode()) {
            case 1 -> 0.0f;
            case 2 -> 0.75f;
            case 3 -> 1.0f;
            default -> state.rainBrightness;
        };
    }

    private static float selectedTime(int mode) {
        return switch (mode) {
            case 1 -> 23000.0f;
            case 2 -> 6000.0f;
            case 3 -> 12000.0f;
            case 4 -> 18000.0f;
            default -> -1.0f;
        };
    }
}
