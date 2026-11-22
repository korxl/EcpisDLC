package dev.ecpis.ecpisdlc.mixin;

import dev.ecpis.ecpisdlc.Ambience;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class LevelWeatherMixin {
    @Inject(method = "method_8430", at = @At("HEAD"), cancellable = true, remap = false)
    private void ecpisdlc$overrideRain(float tickProgress, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel) || !Ambience.INSTANCE.isEnabled()) return;
        switch (Ambience.INSTANCE.weatherMode()) {
            case 1 -> cir.setReturnValue(0.0f);
            case 2 -> cir.setReturnValue(0.75f);
            case 3 -> cir.setReturnValue(1.0f);
            default -> { }
        }
    }

    @Inject(method = "method_8478", at = @At("HEAD"), cancellable = true, remap = false)
    private void ecpisdlc$overrideThunder(float tickProgress, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel) || !Ambience.INSTANCE.isEnabled()) return;
        switch (Ambience.INSTANCE.weatherMode()) {
            case 1, 2 -> cir.setReturnValue(0.0f);
            case 3 -> cir.setReturnValue(1.0f);
            default -> { }
        }
    }
}
