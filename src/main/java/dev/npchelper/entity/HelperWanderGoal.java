package dev.npchelper.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * The one movement every mode is made of: pick a spot around a centre and walk there, then wait a little and pick another.
 * The mode only decides where the centre is and how far the spot may be.
 */
public final class HelperWanderGoal extends Goal {
    /** Ticks between picking the next spot once standing still. */
    private static final int PAUSE_MIN = 20, PAUSE_MAX = 90;
    /** Further than this from the owner in FOLLOW and walking will not catch up any more. */
    private static final double TELEPORT_DISTANCE = 32.0D;
    private static final double SPEED = 0.9D;

    private final NpcHelperEntity helper;
    private int cooldown;

    public HelperWanderGoal(final NpcHelperEntity helper) {
        this.helper = helper;
        setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void tick() {
        Vec3 centre = centre();
        if (centre == null) return;

        if (helper.getMode() == HelperMode.FOLLOW && helper.position().distanceTo(centre) > TELEPORT_DISTANCE) {
            helper.getNavigation().stop();
            helper.teleportTo(centre.x, centre.y, centre.z);
            return;
        }

        if (!helper.getNavigation().isDone()) return;
        if (cooldown-- > 0) return;
        cooldown = PAUSE_MIN + helper.getRandom().nextInt(PAUSE_MAX - PAUSE_MIN);

        // Already where it should be: no need to shuffle every time.
        double radius = radius();
        if (helper.getMode() != HelperMode.FOLLOW && helper.position().distanceTo(centre) <= radius) {
            if (helper.getRandom().nextInt(3) != 0) return;
        }

        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = helper.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = helper.getMode() == HelperMode.FOLLOW
                    ? HelperMode.FOLLOW_MIN + helper.getRandom().nextDouble() * (HelperMode.FOLLOW_MAX - HelperMode.FOLLOW_MIN)
                    : helper.getRandom().nextDouble() * radius;
            double x = centre.x + Math.cos(angle) * distance;
            double z = centre.z + Math.sin(angle) * distance;
            if (helper.getNavigation().moveTo(x, centre.y, z, SPEED)) return;
        }
    }

    /** The owner in FOLLOW, the remembered spot otherwise; null when FOLLOW has nobody to follow. */
    private Vec3 centre() {
        if (helper.getMode() != HelperMode.FOLLOW) return Vec3.atBottomCenterOf(helper.getAnchor());
        LivingEntity owner = helper.getOwner();
        return owner == null || owner.level() != helper.level() ? null : owner.position();
    }

    private double radius() {
        return switch (helper.getMode()) {
            case STAY -> HelperMode.STAY_RADIUS;
            case FOLLOW -> HelperMode.FOLLOW_MAX;
            case ROAM -> helper.getRoamRadius();
        };
    }
}
