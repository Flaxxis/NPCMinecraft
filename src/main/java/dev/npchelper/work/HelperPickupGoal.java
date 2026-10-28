package dev.npchelper.work;

import dev.npchelper.entity.NpcHelperEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Walking to the helper's own harvest. It outranks wandering, so work is finished before strolling resumes. */
public final class HelperPickupGoal extends Goal {
    private final NpcHelperEntity helper;

    public HelperPickupGoal(final NpcHelperEntity helper) {
        this.helper = helper;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return helper.getTarget() == null && helper.pickup().canChase();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        helper.pickup().tickChase();
    }

    @Override
    public void stop() {
        helper.getNavigation().stop();
    }
}
