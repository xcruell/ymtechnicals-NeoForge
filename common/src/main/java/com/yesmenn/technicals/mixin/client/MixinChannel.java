package com.yesmenn.technicals.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import com.yesmenn.technicals.creeper.client.CreeperSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells a Creeper stream which OpenAL source plays it, before its first piece is read, so it can start where the
 * other sources of its channel really are (see {@link CreeperSoundInstance.PcmStream#attach}).
 */
@Mixin(Channel.class)
public abstract class MixinChannel {
    @Shadow
    @Final
    private int source;

    @Inject(method = "attachBufferStream", at = @At("HEAD"))
    private void ymtechnicals$attachCreeperStream(AudioStream stream, CallbackInfo callback) {
        if (stream instanceof CreeperSoundInstance.PcmStream creeper) {
            creeper.attach(source);
        }
    }
}
