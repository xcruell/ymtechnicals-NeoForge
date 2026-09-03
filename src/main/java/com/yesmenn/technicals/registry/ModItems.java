package com.yesmenn.technicals.registry;

import com.yesmenn.technicals.YMTechnicals;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(YMTechnicals.MODID);

    public static final DeferredItem<BlockItem> SOUNDEFFECTZ_3000 =
            ITEMS.register("soundeffectz_3000",
                    () -> new BlockItem(ModBlocks.SOUNDEFFECTZ_3000.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> OBSERVERS_EYE =
            ITEMS.register("observers_eye",
                    () -> new BlockItem(ModBlocks.OBSERVERS_EYE.get(), new Item.Properties()));

}