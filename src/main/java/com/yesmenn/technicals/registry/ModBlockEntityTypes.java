package com.yesmenn.technicals.registry;

import com.yesmenn.technicals.YMTechnicals;
import com.yesmenn.technicals.block.entity.ObserversEyeBlockEntity;

import com.yesmenn.technicals.block.entity.Soundeffectz3000BlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, YMTechnicals.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Soundeffectz3000BlockEntity>> SOUNDEFFECTZ_3000 =
            BLOCK_ENTITY_TYPES.register("soundeffectz_3000",
                    () -> BlockEntityType.Builder.of(
                            Soundeffectz3000BlockEntity::new,
                            ModBlocks.SOUNDEFFECTZ_3000.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ObserversEyeBlockEntity>> OBSERVERS_EYE =
            BLOCK_ENTITY_TYPES.register("observers_eye",
                    () -> BlockEntityType.Builder.of(
                            ObserversEyeBlockEntity::new,
                            ModBlocks.OBSERVERS_EYE.get()
                    ).build(null));

}
