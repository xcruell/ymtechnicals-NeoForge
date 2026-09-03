package com.yesmenn.technicals.block.entity;

import com.yesmenn.technicals.block.ObserversEyeBlock;
import com.yesmenn.technicals.network.OpenObserversEyeScreenPayload;
import com.yesmenn.technicals.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class ObserversEyeBlockEntity extends BlockEntity {
    public static final int PATTERN_BOX = 0;
    public static final int PATTERN_CYLINDER = 1;
    public static final int MODE_ON_OFF = 0;
    public static final int MODE_PULSE = 1;
    public static final int FILTER_PLAYERS = 0;
    public static final int FILTER_MOBS_ANIMALS = 1;
    public static final int FILTER_ADDED_PLAYERS = 2;

    private static final int MAX_SIZE = 64;
    private static final int MAX_OFFSET = 6;
    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final int STONE_BUTTON_TICKS = 20;

    private int sizeX = 8;
    private int sizeY = 4;
    private int sizeZ = 8;
    private int offsetX;
    private int offsetY;
    private int offsetZ;
    private int pattern = PATTERN_BOX;
    private int outputMode = MODE_ON_OFF;
    private int filterMode = FILTER_PLAYERS;
    private boolean preview;
    private int scanCooldown;
    private int pulseTicks;
    private final Set<String> addedPlayers = new LinkedHashSet<>();
    private Set<UUID> lastDetectedEntities = new HashSet<>();

    public ObserversEyeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.OBSERVERS_EYE.get(), pos, state);
    }

    public OpenObserversEyeScreenPayload createOpenScreenPayload() {
        return new OpenObserversEyeScreenPayload(
                worldPosition, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ,
                pattern, outputMode, filterMode, preview, playerListString());
    }

    public void applySettings(int nextSizeX, int nextSizeY, int nextSizeZ,
                              int nextOffsetX, int nextOffsetY, int nextOffsetZ,
                              int nextPattern, int nextOutputMode, int nextFilterMode,
                              boolean nextPreview, String nextPlayers) {
        sizeX = clamp(nextSizeX, 1, MAX_SIZE);
        sizeY = clamp(nextSizeY, 1, MAX_SIZE);
        sizeZ = clamp(nextSizeZ, 1, MAX_SIZE);
        offsetX = clamp(nextOffsetX, -MAX_OFFSET, MAX_OFFSET);
        offsetY = clamp(nextOffsetY, -MAX_OFFSET, MAX_OFFSET);
        offsetZ = clamp(nextOffsetZ, -MAX_OFFSET, MAX_OFFSET);
        pattern = nextPattern == PATTERN_CYLINDER ? PATTERN_CYLINDER : PATTERN_BOX;
        outputMode = nextOutputMode == MODE_PULSE ? MODE_PULSE : MODE_ON_OFF;
        filterMode = clamp(nextFilterMode, FILTER_PLAYERS, FILTER_ADDED_PLAYERS);
        preview = nextPreview;
        setPlayers(nextPlayers);
        markUpdated();
    }

    public AABB detectionBox() {
        double centerX = worldPosition.getX() + 0.5D + offsetX;
        double centerY = worldPosition.getY() + 0.5D + offsetY;
        double centerZ = worldPosition.getZ() + 0.5D + offsetZ;
        return new AABB(
                centerX - sizeX / 2.0D, centerY - sizeY / 2.0D, centerZ - sizeZ / 2.0D,
                centerX + sizeX / 2.0D, centerY + sizeY / 2.0D, centerZ + sizeZ / 2.0D);
    }

    public boolean isCylinder() {
        return pattern == PATTERN_CYLINDER;
    }

    public boolean isPreview() {
        return preview;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ObserversEyeBlockEntity sensor) {
        if (level.isClientSide) {
            return;
        }

        if (sensor.outputMode == MODE_PULSE && sensor.pulseTicks > 0 && --sensor.pulseTicks == 0) {
            sensor.setPowered(false);
        }

        if (--sensor.scanCooldown > 0) {
            return;
        }
        sensor.scanCooldown = SCAN_INTERVAL_TICKS;

        Set<UUID> detectedEntities = sensor.detect((ServerLevel) level);
        boolean detected = !detectedEntities.isEmpty();
        if (sensor.outputMode == MODE_PULSE) {
            boolean hasNewEntity = detectedEntities.stream().anyMatch(id -> !sensor.lastDetectedEntities.contains(id));
            if (hasNewEntity) {
                sensor.pulseTicks = STONE_BUTTON_TICKS;
                sensor.setPowered(true);
            }
        } else {
            sensor.setPowered(detected);
        }
        sensor.lastDetectedEntities = detectedEntities;
    }

    private Set<UUID> detect(ServerLevel level) {
        AABB area = detectionBox();
        Set<UUID> detected = new HashSet<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, this::matches)) {
            detected.add(entity.getUUID());
        }
        return detected;
    }

    private boolean matches(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        if (pattern == PATTERN_CYLINDER && !insideCylinder(entity)) {
            return false;
        }
        if (filterMode == FILTER_PLAYERS) {
            return entity instanceof Player;
        }
        if (filterMode == FILTER_MOBS_ANIMALS) {
            return entity instanceof Mob || entity instanceof Animal;
        }
        if (!(entity instanceof Player player)) {
            return false;
        }
        return addedPlayers.contains(player.getGameProfile().getName().toLowerCase(Locale.ROOT));
    }

    private boolean insideCylinder(LivingEntity entity) {
        double centerX = worldPosition.getX() + 0.5D + offsetX;
        double centerZ = worldPosition.getZ() + 0.5D + offsetZ;
        double radiusX = Math.max(0.5D, sizeX / 2.0D);
        double radiusZ = Math.max(0.5D, sizeZ / 2.0D);
        double normalizedX = (entity.getX() - centerX) / radiusX;
        double normalizedZ = (entity.getZ() - centerZ) / radiusZ;
        return normalizedX * normalizedX + normalizedZ * normalizedZ <= 1.0D;
    }

    private void setPowered(boolean powered) {
        if (level == null || level.isClientSide || getBlockState().getValue(ObserversEyeBlock.POWERED) == powered) {
            return;
        }
        BlockState nextState = getBlockState().setValue(ObserversEyeBlock.POWERED, powered);
        level.setBlock(worldPosition, nextState, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        level.updateNeighborsAt(worldPosition, nextState.getBlock());
        setChanged();
    }

    private void setPlayers(String players) {
        addedPlayers.clear();
        if (players == null || players.isBlank()) {
            return;
        }
        Arrays.stream(players.split(","))
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .limit(32)
                .forEach(addedPlayers::add);
    }

    private String playerListString() {
        return String.join(",", addedPlayers);
    }

    public void markUpdated() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sizeX = tag.contains("SizeX") ? clamp(tag.getInt("SizeX"), 1, MAX_SIZE) : sizeX;
        sizeY = tag.contains("SizeY") ? clamp(tag.getInt("SizeY"), 1, MAX_SIZE) : sizeY;
        sizeZ = tag.contains("SizeZ") ? clamp(tag.getInt("SizeZ"), 1, MAX_SIZE) : sizeZ;
        offsetX = clamp(tag.getInt("OffsetX"), -MAX_OFFSET, MAX_OFFSET);
        offsetY = clamp(tag.getInt("OffsetY"), -MAX_OFFSET, MAX_OFFSET);
        offsetZ = clamp(tag.getInt("OffsetZ"), -MAX_OFFSET, MAX_OFFSET);
        pattern = tag.getInt("Pattern") == PATTERN_CYLINDER ? PATTERN_CYLINDER : PATTERN_BOX;
        outputMode = tag.getInt("OutputMode") == MODE_PULSE ? MODE_PULSE : MODE_ON_OFF;
        filterMode = clamp(tag.getInt("FilterMode"), FILTER_PLAYERS, FILTER_ADDED_PLAYERS);
        preview = tag.getBoolean("Preview");
        setPlayers(tag.getString("Players"));
        pulseTicks = tag.getInt("PulseTicks");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("SizeX", sizeX);
        tag.putInt("SizeY", sizeY);
        tag.putInt("SizeZ", sizeZ);
        tag.putInt("OffsetX", offsetX);
        tag.putInt("OffsetY", offsetY);
        tag.putInt("OffsetZ", offsetZ);
        tag.putInt("Pattern", pattern);
        tag.putInt("OutputMode", outputMode);
        tag.putInt("FilterMode", filterMode);
        tag.putBoolean("Preview", preview);
        tag.putString("Players", playerListString());
        tag.putInt("PulseTicks", pulseTicks);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
