package dev.npchelper.work;

import dev.npchelper.entity.NpcHelperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Breaking one block the way a player does: a continuous swing of the right length, with sound, chips and cracks. Moving on
 * to another block, the block changing under the swing, or swapping the tool all throw the progress away, as they should.
 * What falls out goes straight into the helper's bag — it worked for it.
 */
public final class HelperBreaking {
    /** How far the helper can reach to break something. */
    public static final double REACH = 4.5D;
    /** Ticks per unit of hardness: the vanilla pair — the right tool, and everything else. */
    private static final float WITH_TOOL = 30.0F, BARE = 100.0F;

    private final NpcHelperEntity helper;
    private final HelperPickup pickup;
    private @Nullable BlockPos target;
    private @Nullable BlockState state;
    private ItemStack tool = ItemStack.EMPTY;
    private int ticks;
    private long lastTick = Long.MIN_VALUE;

    public HelperBreaking(final NpcHelperEntity helper, final HelperPickup pickup) {
        this.helper = helper;
        this.pickup = pickup;
    }

    public @Nullable BlockPos target() {
        return target;
    }

    public boolean inReach(final BlockPos pos) {
        return helper.position().distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= REACH * REACH;
    }

    /** One tick of work on this block. True once it is gone. */
    public boolean tick(final BlockPos pos) {
        if (!(helper.level() instanceof ServerLevel level)) return false;

        BlockState current = level.getBlockState(pos);
        ItemStack hand = helper.getMainHandItem();
        // A different block, a changed block or a changed tool: the swing starts over.
        if (!pos.equals(target) || current != state || !ItemStack.isSameItemSameComponents(tool, hand)) {
            reset();
            target = pos.immutable();
            state = current;
            tool = hand.copy();
        }

        float hardness = current.getDestroySpeed(level, pos);
        if (current.isAir() || hardness < 0.0F) {
            reset();
            return false;
        }
        if (!inReach(pos)) return false;
        // One tick counts once, however often this is called.
        if (lastTick == level.getGameTime()) return false;
        lastTick = level.getGameTime();

        float speed = Math.max(1.0F, hand.getDestroySpeed(current));
        float divisor = !current.requiresCorrectToolForDrops() || hand.isCorrectToolForDrops(current) ? WITH_TOOL : BARE;
        int duration = Math.max(1, (int) Math.ceil(hardness * divisor / speed));
        ticks++;

        helper.getLookControl().setLookAt(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (ticks % 5 == 1) helper.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        if (ticks % 4 == 1) {
            var sound = current.getSoundType(level, pos, helper);
            level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 8.0F, sound.getPitch() * 0.5F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, current),
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 2, 0.25D, 0.25D, 0.25D, 0.01D);
        }
        level.destroyBlockProgress(helper.getId(), pos, Math.min(9, ticks * 10 / duration));
        if (ticks < duration) return false;

        // What it breaks falls on the ground and is picked up afterwards, as it would be for anyone.
        var drops = Block.getDrops(current, level, pos, level.getBlockEntity(pos), helper, hand);
        level.destroyBlock(pos, false, helper);
        pickup.drop(pos, drops);
        if (!hand.isEmpty()) hand.hurtAndBreak(1, helper, EquipmentSlot.MAINHAND);
        reset();
        return true;
    }

    /** Drops the swing and clears the cracks. */
    public void reset() {
        if (target != null && helper.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(helper.getId(), target, -1);
        }
        target = null;
        state = null;
        tool = ItemStack.EMPTY;
        ticks = 0;
        lastTick = Long.MIN_VALUE;
    }
}
