package com.yesmenn.technicals.registry;

import com.yesmenn.technicals.YMTechnicals;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, YMTechnicals.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> YM_TECHNICALS =
            CREATIVE_MODE_TABS.register("ym_technicals", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ymtechnicals"))
                    .icon(() -> new ItemStack(ModItems.OBSERVERS_EYE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.OBSERVERS_EYE.get());
                        output.accept(ModItems.SOUNDEFFECTZ_3000.get());
                    })
                    .build());

    //                        output.accept(ModItems.SOUNDEFFECTZ_3000.get());
}