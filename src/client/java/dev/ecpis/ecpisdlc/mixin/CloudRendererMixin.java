package dev.ecpis.ecpisdlc.mixin;

import dev.ecpis.ecpisdlc.Ambience;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
abstract class CloudRendererMixin {
    @Inject(method = "method_62168", at = @At("HEAD"), cancellable = true, remap = false)
    private void ecpisdlc$hideClouds(CallbackInfo ci) {
        Ambience ambience = Ambience.INSTANCE;
        if (ambience.isEnabled() && !ambience.clouds()) ci.cancel();
    }

    @ModifyVariable(method = "method_62168", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private int ecpisdlc$cloudColor(int color) {
        Ambience ambience = Ambience.INSTANCE;
        return ambience.isEnabled() && ambience.clouds() ? ambience.cloudColor() : color;
    }
}
