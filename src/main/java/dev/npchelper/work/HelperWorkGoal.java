package dev.npchelper.work;

import dev.npchelper.entity.NpcHelperEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Works the queue, one step at a time, inside the mode's own circle: where the helper lives is still the mode's business,
 * and what it does there is the queue's. Fighting and collecting its own harvest both come first.
 */
public final class HelperWorkGoal extends Goal {
    /** How far from the centre a helper looks for something to work on, on top of its mode's radius. */
    private static final int SEARCH_MARGIN = 8;
    private static final double SPEED = 1.0D;

    private final NpcHelperEntity helper;
    private @Nullable BlockPos site;

    public HelperWorkGoal(final NpcHelperEntity helper) {
        this.helper = helper;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return helper.getTarget() == null && !helper.queue().isEmpty() && helper.centre() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        helper.breaking().reset();
        helper.getNavigation().stop();
        site = null;
    }

    @Override
    public void tick() {
        HelperStep step = helper.queue().current();
        if (step == null || !helper.queue().tick()) return;
        if (!(helper.level() instanceof ServerLevel level)) return;

        if (HelperStep.GATHER_WOOD.equals(step.type())) {
            gatherWood(level, step);
            return;
        }
        // An unknown step is no reason to stand still forever.
        helper.queue().advance();
    }

    private void gatherWood(final ServerLevel level, final HelperStep step) {
        if (site == null || !level.getBlockState(site).is(BlockTags.LOGS)
                || !HelperBuildings.fellable(level, site)) site = findLog(level);
        if (site == null) {
            // Nothing left to fell within reach of the centre: the step cannot be finished here.
            helper.queue().clear();
            return;
        }

        if (!helper.breaking().inReach(site)) {
            if (helper.getNavigation().isDone() || helper.getNavigation().isStuck()) {
                helper.getNavigation().moveTo(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D, SPEED);
            }
            return;
        }

        helper.getNavigation().stop();
        if (!helper.breaking().tick(site)) return;

        helper.queue().noteProgress();
        step.progress();
        site = null;
        if (step.finished()) helper.queue().advance();
    }

    /** The nearest fellable log around the helper's own centre, so work stays where the player put it. */
    private @Nullable BlockPos findLog(final ServerLevel level) {
        Vec3 centre = helper.centre();
        if (centre == null) return null;
        var logs = HelperTreeSearch.find(level, BlockPos.containing(centre), helper.workRadius() + SEARCH_MARGIN);
        return logs.isEmpty() ? null : logs.getFirst();
    }
}
