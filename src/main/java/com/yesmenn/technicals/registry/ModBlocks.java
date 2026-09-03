package com.yesmenn.technicals.registry;

import com.yesmenn.technicals.YMTechnicals;
import com.yesmenn.technicals.block.ObserversEyeBlock;
import com.yesmenn.technicals.block.Soundeffectz3000Block;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(YMTechnicals.MODID);

    public static final DeferredBlock<Soundeffectz3000Block> SOUNDEFFECTZ_3000 =
            BLOCKS.register("soundeffectz_3000",
                    () -> new Soundeffectz3000Block(
                            BlockBehaviour.Properties.of()
                                    .strength(2.0F, 6.0F)
                                    .sound(SoundType.METAL)
                                    .noOcclusion()
                    ));

    public static final DeferredBlock<ObserversEyeBlock> OBSERVERS_EYE =
            BLOCKS.register("observers_eye",
                    () -> new ObserversEyeBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(2.0F, 6.0F)
                                    .sound(SoundType.METAL)
                    ));

}