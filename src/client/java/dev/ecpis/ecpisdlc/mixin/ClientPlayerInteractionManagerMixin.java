package dev.ecpis.ecpisdlc.mixin;

import dev.ecpis.ecpisdlc.EcpisDLCClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "method_2918", at = @At("HEAD"), remap = false)
    private void ecpisdlc$captureAttack(Player attacker, Entity target, CallbackInfo ci) {
        EcpisDLCClient.onEntityAttack(attacker, target);
    }
}
