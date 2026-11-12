package com.yesmenn.technicals.mixin.fabric;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.yesmenn.technicals.creeper.client.CreeperSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

/**
 * Fabric has no per-sound-instance stream (NeoForge's sound instance extension): Creeper sounds get their audio from
 * the shared decoded song instead of a sound file.
 */
@Mixin(SoundEngine.class)
public abstract class MixinSoundEngineFabric {
    @WrapOperation(method = "play", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundBufferLibrary;getStream(Lnet/minecraft/resources/ResourceLocation;Z)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<AudioStream> ymtechnicals$creeperStream(SoundBufferLibrary buffers,
                                                                      ResourceLocation location, boolean looping,
                                                                      Operation<CompletableFuture<AudioStream>> original,
                                                                      @Local(argsOnly = true) SoundInstance sound) {
        if (sound instanceof CreeperSoundInstance creeper) {
            return creeper.openStream();
        }
        return original.call(buffers, location, looping);
    }
}
