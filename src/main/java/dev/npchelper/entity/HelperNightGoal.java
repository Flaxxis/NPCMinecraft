package dev.npchelper.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Pauses the profession for the night; expedition mode deliberately leaves all night travel disabled. */
public final class HelperNightGoal extends Goal {
    private final NpcHelperEntity helper;
    private @Nullable BlockPos destination;
    private long nextSearch;
    private long lastProgress;
    private double bestDistance;
    private boolean active;
    private boolean settled;
    private int wanderCooldown;
    public static final int RESCUE_TICKS = 20 * 30;
    private long rescueStarted = -1;
    private long nextRescueSearch;
    private long rescuedUntil;
    private String rescueProblem = "";

    public HelperNightGoal(final NpcHelperEntity helper) {
        this.helper = helper;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    public static boolean night(final NpcHelperEntity helper) {
        long time = Math.floorMod(helper.level().getOverworldClockTime(), 24000L);
        return helper.level().dimensionType().hasSkyLight() && time >= 13000 && time < 23000;
    }

    public boolean wanted() { return !helper.isExpedition() && night(helper); }
    public boolean active() { return active; }
    public boolean hasDestination() { return destination != null; }

    public String status() {
        if (helper.level().getGameTime() < rescuedUntil) return "Телепортувався до безпечного нічлігу";
        if (!rescueProblem.isEmpty()) return rescueProblem;
        if (hasDestination()) return "Ніч — іде додому або відпочиває в укритті";
        return "Ніч — шукає дім або вхід до укриття";
    }

    /** Independent of movement goals: floating, combat or a failed route cannot prevent the timeout. */
    public void safetyTick() {
        if (!(helper.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (!wanted() || HelperShelter.indoors(level, helper.blockPosition())) {
            rescueStarted = -1;
            rescueProblem = "";
            return;
        }
        if (rescueStarted < 0) rescueStarted = now;
        if (now - rescueStarted < RESCUE_TICKS || now < nextRescueSearch) return;
        nextRescueSearch = now + 100;
        BlockPos safe = HelperShelter.find(helper, false);
        if (safe == null || !HelperShelter.safeTeleport(helper, safe)) {
            rescueProblem = "Не знайшов безпечної точки для нічного переходу";
            return;
        }
        if (!HelperShelter.indoors(level, safe) && safe.distToCenterSqr(helper.position()) < 1) {
            rescueStarted = now;
            return;
        }
        helper.cancelWorkForRescue();
        helper.setTarget(null);
        helper.teleportTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D);
        helper.setDeltaMovement(Vec3.ZERO);
        helper.fallDistance = 0;
        destination = safe;
        settled = true;
        bestDistance = 0;
        lastProgress = now;
        rescueStarted = -1;
        rescueProblem = "";
        rescuedUntil = now + 80;
    }

    @Override public boolean canUse() {
        return wanted() && helper.getTarget() == null && !helper.isRetreating() && !helper.supports().any() && !helper.pickup().canChase();
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public void start() {
        active = true;
        settled = false;
        wanderCooldown = 0;
        nextSearch = 0;
        destination = null;
        helper.getNavigation().setRequiredPathLength(96);
    }
    @Override public void stop() {
        active = false;
        settled = false;
        destination = null;
        helper.getNavigation().stop();
        helper.getNavigation().setRequiredPathLength(0);
    }

    @Override public void tick() {
        if (!(helper.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (destination != null && !HelperShelter.standable(level, destination)) destination = null;
        if (destination == null) {
            if (now < nextSearch) {
                wander(level, helper.centre(), false);
                return;
            }
            nextSearch = now + 100;
            destination = HelperShelter.find(helper);
            settled = false;
            if (destination != null) helper.getNavigation().stop();
            bestDistance = Double.MAX_VALUE;
            lastProgress = now;
        }
        if (destination == null) {
            wander(level, helper.centre(), false);
            return;
        }
        double distance = destination.distToCenterSqr(helper.position());
        boolean inside = HelperShelter.indoors(level, destination);
        if (settled && (distance > helper.workRadius() * helper.workRadius()
                || (inside && !HelperShelter.indoors(level, helper.blockPosition())))) {
            settled = false;
            bestDistance = Double.MAX_VALUE;
            lastProgress = now;
            helper.getNavigation().stop();
        }
        if (!settled && distance < 0.5D) {
            settled = true;
            helper.getNavigation().stop();
        }
        if (settled) {
            wander(level, Vec3.atBottomCenterOf(destination), inside);
            return;
        }
        if (distance + 0.1D < bestDistance) { bestDistance = distance; lastProgress = now; }
        if (now - lastProgress > 200) { destination = null; helper.getNavigation().stop(); return; }
        if (helper.getNavigation().isDone() || helper.getNavigation().isStuck()) {
            helper.getNavigation().moveTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D, 0, 1.0D);
        }
    }

    /** Small local walks can fill a large allowed radius without almost always sampling outside a small room. */
    private void wander(final ServerLevel level, final @Nullable Vec3 centre, final boolean indoors) {
        if (centre == null || !helper.getNavigation().isDone()) return;
        if (wanderCooldown-- > 0) return;
        wanderCooldown = 20 + helper.getRandom().nextInt(70);
        int radius = helper.workRadius();
        if (helper.position().distanceToSqr(centre) > (double) radius * radius) {
            helper.getNavigation().moveTo(centre.x, centre.y, centre.z, 0, 0.9D);
            return;
        }
        BlockPos feet = helper.blockPosition();
        for (int attempt = 0; attempt < 24; attempt++) {
            BlockPos target = feet.offset(helper.getRandom().nextInt(9) - 4, helper.getRandom().nextInt(3) - 1,
                    helper.getRandom().nextInt(9) - 4);
            if (target.distToCenterSqr(centre) > (double) radius * radius || target.distToCenterSqr(helper.position()) < 1) continue;
            if (!HelperShelter.standable(level, target) || (indoors && !HelperShelter.indoors(level, target))) continue;
            var path = helper.getNavigation().createPath(target, 0);
            if (path == null || !path.canReach()) continue;
            boolean staysInside = true;
            for (int index = 0; index < path.getNodeCount(); index++) {
                var node = path.getNode(index);
                BlockPos at = new BlockPos(node.x, node.y, node.z);
                if (at.distToCenterSqr(centre) > (double) radius * radius
                        || (indoors && !HelperShelter.indoors(level, at))) {
                    staysInside = false;
                    break;
                }
            }
            if (staysInside && helper.getNavigation().moveTo(path, 0.9D)) return;
        }
    }

}
