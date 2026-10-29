package dev.npchelper.entity;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The guard's other half: whoever is attacking a player or another helper nearby becomes its target, whether or not the
 * guard saw it coming — the guard hears the fight. Players fighting each other are left to it.
 */
public final class DefendOthersGoal extends TargetGoal {
    /** Fights further off than this are not heard. */
    private static final double RANGE = 24.0D;
    /** A blow older than this is over and done with. */
    private static final int RECENT = 20 * 5;

    private final NpcHelperEntity helper;
    private @Nullable LivingEntity wanted;

    public DefendOthersGoal(final NpcHelperEntity helper) {
        super(helper, false);
        this.helper = helper;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (helper.getProfession() != HelperProfession.GUARD || helper.tickCount % 10 != 0) return false;
        wanted = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity victim : helper.level().getEntitiesOfClass(LivingEntity.class, helper.getBoundingBox().inflate(RANGE),
                entity -> entity != helper && (entity instanceof Player player && !player.isSpectator() || entity instanceof NpcHelperEntity))) {
            LivingEntity attacker = victim.getLastHurtByMob();
            if (attacker == null || attacker instanceof Player || victim.tickCount - victim.getLastHurtByMobTimestamp() > RECENT) continue;
            if (!helper.mayFight(attacker) || !canAttack(attacker, TargetingConditions.DEFAULT)) continue;
            double distance = helper.distanceToSqr(attacker);
            if (distance < best) { best = distance; wanted = attacker; }
        }
        return wanted != null;
    }

    @Override
    public void start() {
        mob.setTarget(wanted);
        super.start();
    }
}
