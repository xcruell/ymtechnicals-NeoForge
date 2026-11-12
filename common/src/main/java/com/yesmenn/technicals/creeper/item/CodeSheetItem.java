package com.yesmenn.technicals.creeper.item;

import com.yesmenn.technicals.creeper.CreeperCode;
import com.yesmenn.technicals.creeper.block.CreeperBlaster3000BlockEntity;
import com.yesmenn.technicals.creeper.block.CreeperSpeaker3000BlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.OptionalInt;

/** Copies the pairing code of a CreeperBlaster3000 and writes it onto CreeperSpeaker3000s. */
public class CodeSheetItem extends Item {
    private static final String CODE = "CreeperCode";

    public CodeSheetItem(Properties properties) {
        super(properties);
    }

    public static ItemInteractionResult useOn(Level level, BlockPos pos, ItemStack stack, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof CreeperBlaster3000BlockEntity blaster) {
            if (!level.isClientSide) {
                write(stack, blaster.code());
                player.displayClientMessage(Component.translatable("item.ymtechnicals.code_sheet.copied",
                        CreeperCode.squares(blaster.code())), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        OptionalInt code = read(stack);
        if (blockEntity instanceof CreeperSpeaker3000BlockEntity speaker && code.isPresent()) {
            if (!level.isClientSide) {
                speaker.setCode(code.getAsInt());
                player.displayClientMessage(Component.translatable("item.ymtechnicals.code_sheet.applied",
                        CreeperCode.squares(code.getAsInt())), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static void write(ItemStack stack, int code) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(CODE, code);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static OptionalInt read(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(CODE)) {
            return OptionalInt.of(CreeperCode.clamp(tag.getInt(CODE)));
        }
        if (tag.getBoolean("HasCode")) {
            // Code sheet of the old YesMenn mod
            return OptionalInt.of(CreeperCode.of(tag.getInt("Code0"), tag.getInt("Code1"), tag.getInt("Code2")));
        }
        return OptionalInt.empty();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        OptionalInt code = read(stack);
        if (code.isPresent()) {
            tooltip.add(Component.translatable("item.ymtechnicals.code_sheet.code",
                    CreeperCode.squares(code.getAsInt())).withStyle(ChatFormatting.GRAY));
            tooltip.add(CreeperCode.names(code.getAsInt()).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.ymtechnicals.code_sheet.empty").withStyle(ChatFormatting.GRAY));
        }
    }
}
