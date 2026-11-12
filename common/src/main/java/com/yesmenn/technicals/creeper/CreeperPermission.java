package com.yesmenn.technicals.creeper;

import com.yesmenn.technicals.YMTechnicals;
import com.yesmenn.technicals.platform.Platform;
import net.minecraft.server.level.ServerPlayer;

/**
 * Permission nodes of the CreeperBlaster3000 ({@code ymtechnicals.creeperblaster.*}). Permission mods can grant them
 * (NeoForge permission API, Fabric with fabric-permissions-api); without one, the permission level decides.
 */
public enum CreeperPermission {
    /** Add songs by link and delete the own ones. Default: {@code downloadPermissionLevel} (operators). */
    DOWNLOAD("creeperblaster.download"),
    /** Delete songs other players added. Default: operators. */
    DELETE_ANY("creeperblaster.delete_any"),
    /** The operator storage quota and daily limit instead of the player ones. Default: operators. */
    OPERATOR_LIMITS("creeperblaster.operator_limits");

    private static final int OPERATOR_LEVEL = 2;
    private final String name;

    CreeperPermission(String name) {
        this.name = name;
    }

    /** Node name without the mod ID, e.g. {@code creeperblaster.download}. */
    public String nodeName() {
        return name;
    }

    /** Full node, e.g. {@code ymtechnicals.creeperblaster.download}. */
    public String node() {
        return YMTechnicals.MODID + "." + name;
    }

    /** Permission level that grants it when no permission mod decides. */
    public int defaultLevel() {
        return this == DOWNLOAD ? CreeperConfig.get().downloadPermissionLevel : OPERATOR_LEVEL;
    }

    public boolean has(ServerPlayer player) {
        return Platform.hasPermission(player, this);
    }

    /**
     * Without a permission mod: the owner of a single player / LAN world always, everybody else by permission level
     * (single player worlds without cheats would otherwise lock their own owner out).
     */
    public boolean byDefault(ServerPlayer player) {
        return player.server.isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(defaultLevel());
    }
}
