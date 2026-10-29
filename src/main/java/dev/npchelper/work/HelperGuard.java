package dev.npchelper.work;

import dev.npchelper.entity.HelperNavigation;
import dev.npchelper.entity.HelperShelter;
import dev.npchelper.entity.NpcHelperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The guard's round: from one point of its range to the next, all the way round, stopping at each to stand and look about.
 * The fighting itself is the helper's own targeting — the round only puts the guard where it can see.
 */
public final class HelperGuard {
    /** Points to a round; the guard goes round its range in this many legs. */
    private static final int LEGS = 6;
    /** How long it stands at a point, looking about. */
    private static final int PAUSE_MIN = 20 * 3, PAUSE_MAX = 20 * 7;
    /** How often it turns to look somewhere else while standing. */
    private static final int LOOK_EVERY = 25;
    /** Not moving at all for this long, the point is out of reach for now. */
    private static final int STANDING_LIMIT = 20 * 10;
    private static final int WALK_TICKS_PER_BLOCK = 20, WALK_BUDGET_MIN = 20 * 10, WALK_BUDGET_MAX = 20 * 60;
    private static final double SPEED = 0.8D;

    private final NpcHelperEntity helper;
    private int leg;
    private @Nullable BlockPos point;
    private long walkStarted;
    private int walkBudget;
    private long lastMoved;
    private @Nullable Vec3 movedFrom;
    private long pauseUntil;
    private long nextLook;
    private float lookYaw;
    private int pauses;

    public HelperGuard(final NpcHelperEntity helper) { this.helper = helper; }

    public void reset() {
        point = null;
        pauseUntil = 0;
        movedFrom = null;
    }

    /** The point it is walking to, or null while it stands and looks about. */
    public @Nullable BlockPos point() { return point; }
    /** How many times it has stopped to look about; the tests count on it. */
    public int pauses() { return pauses; }

    /** One tick of the round. The round never finishes: going round is the job. */
    public void tick(final ServerLevel level) {
        Vec3 centre = helper.workCentre();
        if (centre == null) return;
        long now = level.getGameTime();
        if (now < pauseUntil) {
            lookAround(now);
            return;
        }
        if (point == null) {
            point = nextPoint(level, centre);
            if (point == null) { pause(now); return; }
            walkStarted = now;
            lastMoved = now;
            movedFrom = helper.position();
            walkBudget = Math.min(WALK_BUDGET_MAX, WALK_BUDGET_MIN + (int) Math.sqrt(point.distToCenterSqr(helper.position())) * WALK_TICKS_PER_BLOCK);
            helper.getNavigation().stop();
        }
        if (point.distToCenterSqr(helper.position()) <= 2.25D) {
            pause(now);
            return;
        }
        if (movedFrom == null || helper.position().distanceTo(movedFrom) >= 0.5D) {
            movedFrom = helper.position();
            lastMoved = now;
            helper.queue().noteProgress();
        }
        // A point it cannot get to is simply skipped: the next one may do.
        if (now - lastMoved > STANDING_LIMIT || now - walkStarted > walkBudget) {
            pause(now);
            return;
        }
        if (helper.getNavigation().isDone() || helper.getNavigation().isStuck()) {
            if (!helper.getNavigation().moveTo(point.getX() + 0.5D, point.getY(), point.getZ() + 0.5D, SPEED)) pause(now);
        }
    }

    private void pause(final long now) {
        point = null;
        helper.getNavigation().stop();
        pauseUntil = now + PAUSE_MIN + helper.getRandom().nextInt(PAUSE_MAX - PAUSE_MIN);
        nextLook = now;
        pauses++;
        helper.queue().noteProgress();
    }

    /** Turns this way and that while it stands, so a monster on any side comes into view. */
    private void lookAround(final long now) {
        if (now >= nextLook) {
            nextLook = now + LOOK_EVERY;
            lookYaw = helper.getYRot() + (helper.getRandom().nextFloat() - 0.5F) * 240.0F;
        }
        double radians = Math.toRadians(lookYaw);
        Vec3 eye = helper.getEyePosition();
        // Minecraft's yaw: 0 faces south (+z), 90 faces west (-x).
        helper.getLookControl().setLookAt(eye.x - Math.sin(radians) * 8.0D, eye.y, eye.z + Math.cos(radians) * 8.0D);
    }

    /** The next point of the round: round the centre in legs, well out towards the edge of the range. */
    private @Nullable BlockPos nextPoint(final ServerLevel level, final Vec3 centre) {
        int radius = helper.workRadius();
        for (int attempt = 0; attempt < 6; attempt++) {
            leg = (leg + 1) % LEGS;
            double angle = leg * (Math.PI * 2.0D / LEGS) + (helper.getRandom().nextDouble() - 0.5D) * 0.5D;
            double distance = Math.max(2.0D, radius * (0.5D + helper.getRandom().nextDouble() * 0.4D));
            int x = (int) Math.floor(centre.x + Math.cos(angle) * distance);
            int z = (int) Math.floor(centre.z + Math.sin(angle) * distance);
            BlockPos spot = ground(level, x, (int) Math.floor(centre.y), z);
            if (spot == null) continue;
            var path = HelperNavigation.probe(helper, spot, 1);
            if (path != null && path.canReach()) return spot;
        }
        return null;
    }

    private static @Nullable BlockPos ground(final ServerLevel level, final int x, final int y, final int z) {
        for (int dy = 4; dy >= -4; dy--) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            if (HelperShelter.standable(level, pos)) return pos;
        }
        return null;
    }
}
