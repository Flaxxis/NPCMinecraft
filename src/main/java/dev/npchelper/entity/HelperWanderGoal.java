package dev.npchelper.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * The one movement every mode is made of: pick a spot around a centre and walk there, then wait a little and pick another.
 * The mode only decides where the centre is and how far the spot may be.
 */
public final class HelperWanderGoal extends Goal {
    /** Ticks between picking the next spot once standing still. */
    private static final int PAUSE_MIN = 20, PAUSE_MAX = 90;
    /** Further than this from the owner and walking will not catch up any more. */
    private static final double TELEPORT_DISTANCE = 32.0D;
    private static final double SPEED = 0.9D;

    private final NpcHelperEntity helper;
    private int cooldown;

    public HelperWanderGoal(final NpcHelperEntity helper) {
        this.helper = helper;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
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
        Vec3 centre = helper.centre();
        if (centre == null) return;

        if (followsOwner() && helper.position().distanceTo(centre) > TELEPORT_DISTANCE) {
            teleportNear(centre);
            return;
        }

        if (!helper.getNavigation().isDone()) return;
        if (cooldown-- > 0) return;
        cooldown = PAUSE_MIN + helper.getRandom().nextInt(PAUSE_MAX - PAUSE_MIN);

        // Already where it should be: no need to shuffle on every pause.
        double radius = radius();
        if (!followsOwner() && helper.position().distanceTo(centre) <= radius && helper.getRandom().nextInt(3) != 0) return;

        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = helper.getRandom().nextDouble() * Math.PI * 2.0D;
            // Never right on top of the centre, and never past the range the player set.
            double near = Math.min(HelperMode.MIN_DISTANCE, radius);
            double distance = near + helper.getRandom().nextDouble() * Math.max(0.0D, radius - near);
            double x = centre.x + Math.cos(angle) * distance;
            double z = centre.z + Math.sin(angle) * distance;
            if (helper.getNavigation().moveTo(x, centre.y, z, SPEED)) return;
        }
    }

    private boolean followsOwner() {
        return helper.getMode() == HelperMode.FOLLOW || helper.isRetreating();
    }

    /** One setting, whichever mode it is in: the range the player chose. */
    private double radius() {
        return helper.workRadius();
    }

    /** Standing room only: never inside a block and never on top of something that hurts. */
    private void teleportNear(final Vec3 centre) {
        helper.getNavigation().stop();
        BlockPos around = BlockPos.containing(centre);
        for (int attempt = 0; attempt < 16; attempt++) {
            BlockPos spot = around.offset(
                    helper.getRandom().nextInt(7) - 3,
                    helper.getRandom().nextInt(3) - 1,
                    helper.getRandom().nextInt(7) - 3);
            if (standable(spot)) {
                helper.teleportTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
                return;
            }
        }
    }

    private boolean standable(final BlockPos spot) {
        var level = helper.level();
        var ground = level.getBlockState(spot.below());
        return ground.isSolid()
                && ground.getFluidState().isEmpty()
                && level.getBlockState(spot).isAir()
                && level.getBlockState(spot.above()).isAir();
    }
}
