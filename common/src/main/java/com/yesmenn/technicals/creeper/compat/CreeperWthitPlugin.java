package com.yesmenn.technicals.creeper.compat;

import com.yesmenn.technicals.creeper.CreeperCode;
import com.yesmenn.technicals.creeper.block.CreeperBlaster3000Block;
import com.yesmenn.technicals.creeper.block.CreeperBlaster3000BlockEntity;
import com.yesmenn.technicals.creeper.block.CreeperSpeaker3000Block;
import com.yesmenn.technicals.creeper.block.CreeperSpeaker3000BlockEntity;
import com.yesmenn.technicals.creeper.client.CreeperClientState;
import com.yesmenn.technicals.creeper.network.ChannelState;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * WTHIT (optional, client only): what a blaster or speaker plays, with position; its code as colored squares, range
 * and volume while Shift is held. Everything comes from data the client has anyway (block entity sync and channel states), so no server
 * side plugin is needed. Registered in waila_plugins.json.
 */
public final class CreeperWthitPlugin implements IWailaClientPlugin {
    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(new BlasterProvider(), CreeperBlaster3000Block.class);
        registrar.body(new SpeakerProvider(), CreeperSpeaker3000Block.class);
    }

    private static void song(ITooltip tooltip, String title, ChannelState.Mode mode, long position, long length) {
        if (title.isEmpty()) {
            tooltip.addLine(Component.translatable("gui.ymtechnicals.creeper_blaster.no_song")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.addLine(Component.literal("♪ ").append(title).withStyle(ChatFormatting.WHITE));
        MutableComponent state = Component.translatable(switch (mode) {
            case PLAYING -> "gui.ymtechnicals.creeper_blaster.playing";
            case PAUSED -> "gui.ymtechnicals.creeper_blaster.paused";
            default -> "gui.ymtechnicals.creeper_blaster.stopped";
        });
        if (mode != ChannelState.Mode.STOPPED) {
            state.append(" " + time(position) + (length > 0 ? " / " + time(length) : ""));
        }
        tooltip.addLine(state.withStyle(ChatFormatting.GRAY));
    }

    /** Code, range and volume only while Shift is held; otherwise a short hint. */
    private static void settings(ITooltip tooltip, int code, int range, int volume) {
        if (!Screen.hasShiftDown()) {
            tooltip.addLine(Component.translatable("gui.ymtechnicals.creeper.wthit.shift_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.addLine(Component.translatable("gui.ymtechnicals.creeper.code").append(": ")
                .withStyle(ChatFormatting.GRAY).append(CreeperCode.squares(code)));
        tooltip.addLine(Component.translatable("gui.ymtechnicals.creeper.wthit.range_volume", range, volume)
                .withStyle(ChatFormatting.GRAY));
    }

    private static String time(long ticks) {
        long seconds = Math.max(0, ticks / 20);
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    private static final class BlasterProvider implements IBlockComponentProvider {
        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlockEntity() instanceof CreeperBlaster3000BlockEntity blaster) {
                song(tooltip, blaster.title(), blaster.mode(),
                        blaster.positionTicks(accessor.getLevel().getGameTime()), blaster.songTicks());
                settings(tooltip, blaster.code(), blaster.range(), blaster.volume());
            }
        }
    }

    private static final class SpeakerProvider implements IBlockComponentProvider {
        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof CreeperSpeaker3000BlockEntity speaker)) {
                return;
            }
            ChannelState channel = CreeperClientState.channel(speaker.code());
            if (channel == null) {
                tooltip.addLine(Component.translatable("gui.ymtechnicals.creeper_speaker.no_blaster")
                        .withStyle(ChatFormatting.GRAY));
            } else {
                song(tooltip, channel.title(), channel.mode(),
                        channel.positionTicks(accessor.getLevel().getGameTime()), channel.songTicks());
            }
            settings(tooltip, speaker.code(), speaker.range(), speaker.volume());
        }
    }
}
