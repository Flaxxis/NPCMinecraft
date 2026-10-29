package dev.npchelper.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.pathfinder.Path;
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

    /**
     * A path handed over ready-made (the keeper plans its own) leaves the navigation's target where an older walk put it.
     * Vanilla plans again whenever a block near the path changes — the helper's own door opening is one — and would head
     * for that old spot: it opened the door, turned round and walked off. Plan again to where this path was going instead.
     */
    @Override
    public void recomputePath() {
        Path current = getPath();
        if (current != null && !current.isDone() && !current.getTarget().equals(getTargetPos())) {
            Path fresh = createPath(current.getTarget(), 1);
            if (fresh == null || !moveTo(fresh, speedModifier)) stop();
            return;
        }
        super.recomputePath();
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
        // The path may have to go round a house to its door, so allow a walk well over twice the straight line.
        int radius = Math.max(64, Math.min(160, 2 * (int) Math.ceil(helper.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(target))) + 32));
        BlockPos from = helper.blockPosition();
        var region = new net.minecraft.world.level.PathNavigationRegion(helper.level(), from.offset(-radius, -radius, -radius), from.offset(radius, radius, radius));
        var finder = new PathFinder(evaluator(), radius * 16);
        return finder.findPath(region, helper, java.util.Set.of(target), radius, reach, 1.0F);
    }

}
