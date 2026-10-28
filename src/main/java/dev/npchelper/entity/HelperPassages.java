package dev.npchelper.entity;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

/** Opens only passages on the active route and closes them after crossing, including initially open ones. */
public final class HelperPassages {
    private record Crossing(double entrySide, long since) { }
    private final NpcHelperEntity helper;
    private final Map<BlockPos, Crossing> crossed = new HashMap<>();

    public HelperPassages(final NpcHelperEntity helper) { this.helper = helper; }

    public static boolean usable(final BlockState state) {
        return DoorBlock.isWoodenDoor(state) || state.getBlock() instanceof FenceGateBlock;
    }

    private static BlockPos bottom(final BlockPos pos, final BlockState state) {
        return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    private double side(final BlockPos pos, final BlockState state) {
        Direction facing = state.getBlock() instanceof DoorBlock ? state.getValue(DoorBlock.FACING) : state.getValue(FenceGateBlock.FACING);
        return (helper.getX() - pos.getX() - 0.5D) * facing.getStepX()
                + (helper.getZ() - pos.getZ() - 0.5D) * facing.getStepZ();
    }

    public void tick() {
        if (!(helper.level() instanceof ServerLevel level)) return;
        var path = helper.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            for (int index = path.getNextNodeIndex(); index < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 4); index++) {
                var node = path.getNode(index);
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = new BlockPos(node.x, node.y + dy, node.z);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (!usable(state)) continue;
                    pos = bottom(pos, state);
                    state = level.getBlockState(pos);
                    if (pos.distToCenterSqr(helper.position()) > 9.0D) continue;
                    if (!crossed.containsKey(pos)) crossed.put(pos.immutable(), new Crossing(side(pos, state), level.getGameTime()));
                    setOpen(level, pos, state, true);
                }
            }
        }
        for (var entry : Map.copyOf(crossed).entrySet()) {
            BlockPos pos = entry.getKey();
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!usable(state)) { crossed.remove(pos); continue; }
            double now = side(pos, state);
            boolean passed = now * entry.getValue().entrySide() < 0 && Math.abs(now) > 0.9D;
            boolean abandoned = level.getGameTime() - entry.getValue().since() > 100 && pos.distToCenterSqr(helper.position()) > 9;
            if (!passed && !abandoned) continue;
            AABB opening = new AABB(pos).expandTowards(0, state.getBlock() instanceof DoorBlock ? 1 : 0.5D, 0).inflate(0.25D, 0, 0.25D);
            if (!level.getEntitiesOfClass(LivingEntity.class, opening).isEmpty()) continue;
            setOpen(level, pos, state, false);
            crossed.remove(pos);
        }
    }

    public void store(final net.minecraft.world.level.storage.ValueOutput output) {
        var list = output.childrenList("Crossings");
        for (var entry : crossed.entrySet()) {
            var child = list.addChild();
            child.store("Pos", BlockPos.CODEC, entry.getKey());
            child.putDouble("Side", entry.getValue().entrySide());
        }
    }

    public void load(final net.minecraft.world.level.storage.ValueInput input) {
        crossed.clear();
        for (var child : input.childrenListOrEmpty("Crossings")) {
            child.read("Pos", BlockPos.CODEC).ifPresent(pos -> crossed.put(pos,
                    new Crossing(child.getDoubleOr("Side", 0), helper.level().getGameTime())));
        }
    }

    private void setOpen(final ServerLevel level, final BlockPos pos, final BlockState state, final boolean open) {
        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(helper, level, state, pos, open);
        } else if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN) != open) {
            level.setBlockAndUpdate(pos, state.setValue(FenceGateBlock.OPEN, open));
            level.playSound(null, pos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(helper, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        }
    }
}
