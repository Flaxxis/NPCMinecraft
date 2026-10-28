package dev.npchelper.entity;

import dev.npchelper.work.HelperBuildings;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Getting out of a fix the way a player would: break what is in the way beside or above, and if the only way is down,
 * take a short jump. The block underfoot is never touched, and nothing a player built is broken to get free.
 */
public final class HelperUnstickGoal extends Goal {
    /** Moving this far counts as free again. */
    private static final double FREED = 1.5D;
    /** Breaking through for this long without getting anywhere means it is not working. */
    private static final int GIVE_UP_TICKS = 20 * 20;

    private final NpcHelperEntity helper;
    private @Nullable Vec3 stuckAt;
    private @Nullable BlockPos digging;
    private int ticks;

    public HelperUnstickGoal(final NpcHelperEntity helper) {
        this.helper = helper;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return helper.getTarget() == null && helper.isStuck();
    }

    @Override
    public boolean canContinueToUse() {
        if (stuckAt == null || helper.position().distanceTo(stuckAt) > FREED) return false;
        return ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        stuckAt = helper.position();
        digging = null;
        ticks = 0;
        helper.getNavigation().stop();
    }

    @Override
    public void stop() {
        helper.breaking().reset();
        helper.clearStuck();
        helper.setEscaping(false);
        stuckAt = null;
        digging = null;
    }

    @Override
    public void tick() {
        ticks++;
        if (!(helper.level() instanceof ServerLevel level)) return;
        Vec3 goal = helper.centre();
        if (goal == null) goal = helper.position();
        BlockPos feet = helper.blockPosition();

        // First: something solid in the way, beside or overhead, that may be broken.
        if (digging == null || level.getBlockState(digging).isAir()) digging = obstruction(level, feet, goal);
        if (digging != null) {
            helper.getNavigation().stop();
            helper.breaking().tick(digging);
            return;
        }

        // Nothing to break and the ground is below: allow a short jump down and head for the centre.
        if (goal.y < feet.getY() - 0.5D || dropBelow(level, feet) > 0) {
            helper.setEscaping(true);
            if (helper.getNavigation().isDone() || helper.getNavigation().isStuck()) {
                helper.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0D);
            }
            return;
        }

        // The centre is above: climb on its own dirt.
        if (goal.y > feet.getY() + 1.5D) helper.supports().step(level, BlockPos.containing(goal));
    }

    /** The first breakable block beside the feet or the head, or overhead, the side facing the centre tried first. */
    private @Nullable BlockPos obstruction(final ServerLevel level, final BlockPos feet, final Vec3 goal) {
        Direction towards = Direction.getApproximateNearest(goal.x - feet.getX(), 0.0D, goal.z - feet.getZ());
        List<BlockPos> candidates = new ArrayList<>();
        for (Direction side : List.of(towards, towards.getClockWise(), towards.getCounterClockWise(), towards.getOpposite())) {
            candidates.add(feet.relative(side));
            candidates.add(feet.relative(side).above());
        }
        // Overhead only when the way out is up.
        if (goal.y > feet.getY() + 1.5D) candidates.add(feet.above(2));

        for (BlockPos pos : candidates) {
            if (breakable(level, pos)) return pos.immutable();
        }
        return null;
    }

    /** Solid, natural and nobody's: never a crafted block, a block with contents, a liquid, or bedrock. */
    private boolean breakable(final ServerLevel level, final BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (state.getCollisionShape(level, pos).isEmpty()) return false;
        if (state.getDestroySpeed(level, pos) < 0.0F) return false;
        if (HelperBuildings.crafted(state) || level.getBlockEntity(pos) != null) return false;
        // Its own dirt is fair game; anybody's construction is not.
        return !HelperBuildings.nearCrafted(level, pos) || helper.supports().owns(pos);
    }

    /** How many blocks of air lie under the feet before the ground. */
    private int dropBelow(final ServerLevel level, final BlockPos feet) {
        int drop = 0;
        BlockPos below = feet.below();
        while (drop < 16 && level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            drop++;
            below = below.below();
        }
        return drop;
    }
}
