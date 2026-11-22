package dev.ecpis.ecpisdlc.mixin;

import dev.ecpis.ecpisdlc.Ambience;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Inject(method = "method_3211", at = @At("RETURN"), cancellable = true, remap = false)
    private void ecpisdlc$overrideFogColor(Camera camera, int viewDistance, DeltaTracker tickCounter,
                                           float tickProgress, ClientLevel world,
                                           CallbackInfoReturnable<Vector4f> cir) {
        Ambience ambience = Ambience.INSTANCE;
        if (!ambience.isEnabled() || ambience.fogMode() == 0) return;
        Vector4f current = cir.getReturnValue();
        int color = ambience.fogColor();
        cir.setReturnValue(new Vector4f(
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f,
                current.w
        ));
    }

    @ModifyVariable(method = "method_71110", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private float ecpisdlc$environmentFogStart(float current) {
        return fogStart(current);
    }

    @ModifyVariable(method = "method_71110", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false)
    private float ecpisdlc$environmentFogEnd(float current) {
        return fogEnd(current);
    }

    @ModifyVariable(method = "method_71110", at = @At("HEAD"), argsOnly = true, ordinal = 2, remap = false)
    private float ecpisdlc$renderFogStart(float current) {
        return fogStart(current);
    }

    @ModifyVariable(method = "method_71110", at = @At("HEAD"), argsOnly = true, ordinal = 3, remap = false)
    private float ecpisdlc$renderFogEnd(float current) {
        return fogEnd(current);
    }

    @ModifyVariable(method = "method_71110", at = @At("HEAD"), argsOnly = true, ordinal = 4, remap = false)
    private float ecpisdlc$skyFogEnd(float current) {
        return fogEnd(current);
    }

    @ModifyVariable(method = "method_71110", at = @At("HEAD"), argsOnly = true, ordinal = 5, remap = false)
    private float ecpisdlc$cloudFogEnd(float current) {
        return fogEnd(current);
    }

    private static float fogStart(float current) {
        Ambience ambience = Ambience.INSTANCE;
        return ambience.isEnabled() && ambience.fogMode() > 0 ? fogDistance(ambience.fogMode()) * 0.35f : current;
    }

    private static float fogEnd(float current) {
        Ambience ambience = Ambience.INSTANCE;
        return ambience.isEnabled() && ambience.fogMode() > 0 ? fogDistance(ambience.fogMode()) : current;
    }

    private static float fogDistance(int mode) {
        return mode == 1 ? 128.0f : 48.0f;
    }
}
