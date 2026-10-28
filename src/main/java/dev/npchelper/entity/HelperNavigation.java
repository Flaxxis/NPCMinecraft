package dev.npchelper.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** Wooden doors and gates are usable path nodes; the passage controller opens them before collision. */
public final class HelperNavigation extends GroundPathNavigation {
    public HelperNavigation(final Mob mob, final Level level) { super(mob, level); }

    @Override
    protected PathFinder createPathFinder(final int maxVisitedNodes) {
        nodeEvaluator = evaluator();
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    private static WalkNodeEvaluator evaluator() {
        WalkNodeEvaluator evaluator = new WalkNodeEvaluator() {
            @Override
            public PathType getPathType(final PathfindingContext context, final int x, final int y, final int z) {
                var state = context.getBlockState(new BlockPos(x, y, z));
                if (state.getBlock() instanceof FenceGateBlock) return PathType.WALKABLE_DOOR;
                return super.getPathType(context, x, y, z);
            }
        };
        evaluator.setCanOpenDoors(true);
        evaluator.setCanPassDoors(true);
        return evaluator;
    }
    public static net.minecraft.world.level.pathfinder.@org.jspecify.annotations.Nullable Path probe(
            final NpcHelperEntity helper, final BlockPos target, final int reach) {
        int radius = Math.max(32, Math.min(160, (int) Math.ceil(helper.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(target))) + 16));
        BlockPos from = helper.blockPosition();
        var region = new net.minecraft.world.level.PathNavigationRegion(helper.level(), from.offset(-radius, -radius, -radius), from.offset(radius, radius, radius));
        var finder = new PathFinder(evaluator(), radius * 16);
        return finder.findPath(region, helper, java.util.Set.of(target), radius, reach, 1.0F);
    }

}
