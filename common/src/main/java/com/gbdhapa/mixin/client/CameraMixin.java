package com.gbdhapa.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.Camera.class)
public class CameraMixin {

    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private void onSetup(net.minecraft.world.level.BlockGetter level, net.minecraft.world.entity.Entity entity,
                         boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().player == null) {
            ci.cancel();
        }
    }
}
