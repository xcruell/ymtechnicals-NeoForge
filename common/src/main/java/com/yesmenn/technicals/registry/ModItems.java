package com.yesmenn.technicals.registry;

import com.yesmenn.technicals.creeper.item.CodeSheetItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import com.yesmenn.technicals.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.function.Supplier;

public class ModItems {

    public static final Supplier<BlockItem> SOUNDEFFECTZ_3000 = Platform.register(BuiltInRegistries.ITEM, "soundeffectz_3000",
                    () -> new BlockItem(ModBlocks.SOUNDEFFECTZ_3000.get(), new Item.Properties()));

    public static final Supplier<BlockItem> OBSERVERS_EYE = Platform.register(BuiltInRegistries.ITEM, "observers_eye",
                    () -> new BlockItem(ModBlocks.OBSERVERS_EYE.get(), new Item.Properties()));

    public static final Supplier<BlockItem> CREEPER_BLASTER_3000 = Platform.register(BuiltInRegistries.ITEM, "creeper_blaster_3000",
                    () -> new BlockItem(ModBlocks.CREEPER_BLASTER_3000.get(), new Item.Properties()));

    public static final Supplier<BlockItem> CREEPER_SPEAKER_3000 = Platform.register(BuiltInRegistries.ITEM, "creeper_speaker_3000",
                    () -> new BlockItem(ModBlocks.CREEPER_SPEAKER_3000.get(), new Item.Properties()));

    public static final Supplier<CodeSheetItem> CODE_SHEET = Platform.register(BuiltInRegistries.ITEM, "code_sheet", () -> new CodeSheetItem(new Item.Properties().stacksTo(16)));


    public static void init() {
    }
}
