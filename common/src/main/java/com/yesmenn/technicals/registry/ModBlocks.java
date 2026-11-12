package com.yesmenn.technicals.registry;

import com.yesmenn.technicals.block.ObserversEyeBlock;
import com.yesmenn.technicals.block.Soundeffectz3000Block;
import com.yesmenn.technicals.creeper.block.CreeperBlaster3000Block;
import com.yesmenn.technicals.creeper.block.CreeperSpeaker3000Block;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import com.yesmenn.technicals.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.function.Supplier;

public class ModBlocks {

    public static final Supplier<Soundeffectz3000Block> SOUNDEFFECTZ_3000 = Platform.register(BuiltInRegistries.BLOCK, "soundeffectz_3000",
                    () -> new Soundeffectz3000Block(
                            BlockBehaviour.Properties.of()
                                    .strength(2.0F, 6.0F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()
                    ));

    public static final Supplier<ObserversEyeBlock> OBSERVERS_EYE = Platform.register(BuiltInRegistries.BLOCK, "observers_eye",
                    () -> new ObserversEyeBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(2.0F, 6.0F)
                                    .sound(SoundType.METAL)
                    ));

    public static final Supplier<CreeperBlaster3000Block> CREEPER_BLASTER_3000 = Platform.register(BuiltInRegistries.BLOCK, "creeper_blaster_3000",
                    () -> new CreeperBlaster3000Block(BlockBehaviour.Properties.of()
                            .strength(3.0F, 6.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    public static final Supplier<CreeperSpeaker3000Block> CREEPER_SPEAKER_3000 = Platform.register(BuiltInRegistries.BLOCK, "creeper_speaker_3000",
                    () -> new CreeperSpeaker3000Block(BlockBehaviour.Properties.of()
                            .strength(2.0F, 6.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));


    /** Loads the class, which registers the blocks (before items and block entity types). */
    public static void init() {
    }
}
