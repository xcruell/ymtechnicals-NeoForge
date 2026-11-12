package com.yesmenn.technicals.registry;

import com.yesmenn.technicals.block.entity.ObserversEyeBlockEntity;

import com.yesmenn.technicals.block.entity.Soundeffectz3000BlockEntity;
import com.yesmenn.technicals.creeper.block.CreeperBlaster3000BlockEntity;
import com.yesmenn.technicals.creeper.block.CreeperSpeaker3000BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.yesmenn.technicals.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.function.Supplier;

public class ModBlockEntityTypes {

    public static final Supplier<BlockEntityType<Soundeffectz3000BlockEntity>> SOUNDEFFECTZ_3000 = Platform.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, "soundeffectz_3000",
                    () -> BlockEntityType.Builder.of(
                            Soundeffectz3000BlockEntity::new,
                            ModBlocks.SOUNDEFFECTZ_3000.get()
                    ).build(null));

    public static final Supplier<BlockEntityType<ObserversEyeBlockEntity>> OBSERVERS_EYE = Platform.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, "observers_eye",
                    () -> BlockEntityType.Builder.of(
                            ObserversEyeBlockEntity::new,
                            ModBlocks.OBSERVERS_EYE.get()
                    ).build(null));

    public static final Supplier<BlockEntityType<CreeperBlaster3000BlockEntity>> CREEPER_BLASTER_3000 = Platform.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, "creeper_blaster_3000",
                    () -> BlockEntityType.Builder.of(CreeperBlaster3000BlockEntity::new,
                            ModBlocks.CREEPER_BLASTER_3000.get()).build(null));

    public static final Supplier<BlockEntityType<CreeperSpeaker3000BlockEntity>> CREEPER_SPEAKER_3000 = Platform.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, "creeper_speaker_3000",
                    () -> BlockEntityType.Builder.of(CreeperSpeaker3000BlockEntity::new,
                            ModBlocks.CREEPER_SPEAKER_3000.get()).build(null));


    public static void init() {
    }
}
