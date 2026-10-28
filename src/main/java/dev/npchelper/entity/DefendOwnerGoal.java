package dev.npchelper.entity;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

/** Whoever is hitting the owner becomes the helper's target, as long as the owner is near enough to be worth defending. */
public final class DefendOwnerGoal extends TargetGoal {
    /** An owner further than this is on their own: the helper is somewhere else doing something else. */
    private static final double OWNER_RANGE = 16.0D;

    private final NpcHelperEntity helper;
    private LivingEntity wanted;
    private int seenTimestamp;

    public DefendOwnerGoal(final NpcHelperEntity helper) {
        super(helper, false);
        this.helper = helper;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = helper.getOwner();
        if (owner == null || owner.level() != helper.level() || helper.distanceTo(owner) > OWNER_RANGE) return false;

        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker == null || attacker == helper || !helper.mayFight(attacker)) return false;

        // The same blow must not start the goal twice.
        int timestamp = owner.getLastHurtByMobTimestamp();
        if (timestamp == seenTimestamp) return false;
        seenTimestamp = timestamp;

        wanted = attacker;
        return canAttack(wanted, TargetingConditions.DEFAULT);
    }

    @Override
    public void start() {
        mob.setTarget(wanted);
        super.start();
    }
}
