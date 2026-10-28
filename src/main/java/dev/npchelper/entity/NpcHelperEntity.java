package dev.npchelper.entity;

import dev.npchelper.inventory.HelperMenu;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A crafted helper. Owner, name, mode, bag and gear live on the helper, not on the player, so one world can hold several of
 * them and each keeps its own across a reload.
 */
public final class NpcHelperEntity extends PathfinderMob implements OwnableEntity {
    public static final int BAG_SIZE = 27;
    /** What the range steps through when the player clicks it. It means the same in every mode. */
    public static final int[] ROAM_RADII = {4, 8, 16, 32, 64};
    /** Hostiles further than this are somebody else's problem. */
    public static final double GUARD_RANGE = 16.0D;
    /** A chase that strays this far from the centre is called off. */
    private static final double CHASE_LEASH = 24.0D;
    /** Below this share of its health the helper breaks off and keeps to the owner. */
    private static final float RETREAT_HEALTH = 0.3F;
    private static final int RETREAT_TICKS = 20 * 10;
    /** Never fall further than this of its own accord. */
    private static final int MAX_FALL = 2;
    /** A breath between one job and the next, so a trade is a working day rather than a treadmill. */
    private static final int REST_TICKS = 20 * 15;

    private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER = SynchedEntityData.defineId(
            NpcHelperEntity.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    /** Synched, so the interface shows these without asking the server. */
    private static final EntityDataAccessor<String> DATA_MODE = SynchedEntityData.defineId(
            NpcHelperEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_PROFESSION = SynchedEntityData.defineId(
            NpcHelperEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_ROAM_RADIUS = SynchedEntityData.defineId(
            NpcHelperEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Boolean> DATA_EXPEDITION = SynchedEntityData.defineId(
            NpcHelperEntity.class, EntityDataSerializers.BOOLEAN);
    private HelperNightGoal nightGoal;
    private final HelperPassages passages = new HelperPassages(this);
    private final HelperEating eating = new HelperEating(this);

    private final SimpleContainer bag = new SimpleContainer(BAG_SIZE);
    private final dev.npchelper.work.HelperPickup pickup = new dev.npchelper.work.HelperPickup(this);
    private final dev.npchelper.work.HelperBreaking breaking = new dev.npchelper.work.HelperBreaking(this, pickup);
    private final dev.npchelper.work.HelperQueue queue = new dev.npchelper.work.HelperQueue();
    private final dev.npchelper.work.HelperSupports supports = new dev.npchelper.work.HelperSupports(this);
    /** Ticks before a finished trade looks for the next job of its own. */
    private int restTicks;
    private dev.npchelper.work.HelperWorkGoal workGoal;
    /** The spot the helper keeps to in every mode but FOLLOW. */
    private BlockPos anchor = BlockPos.ZERO;
    /** While this has not run out, the helper keeps to the owner whatever its mode says. */
    private int retreatTicks;
    /** The bench it set down for its own work, to be taken back when the work is done. */
    private @Nullable BlockPos placedBench;

    public NpcHelperEntity(final EntityType<? extends NpcHelperEntity> type, final Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        // Nothing worth walking into: the navigation refuses to plan a path through any of these.
        setPathfindingMalus(PathType.LAVA, -1.0F);
        setPathfindingMalus(PathType.DAMAGING, -1.0F);
        setPathfindingMalus(PathType.DAMAGE_CAUTIOUS, -1.0F);
        setPathfindingMalus(PathType.FIRE, -1.0F);
        setPathfindingMalus(PathType.DAMAGING_IN_NEIGHBOR, 8.0F);
        setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 8.0F);
        setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
        setPathfindingMalus(PathType.ON_TOP_OF_POWDER_SNOW, -1.0F);
        getNavigation().setCanFloat(true);
    }

    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(final Level level) {
        return new HelperNavigation(this, level);
    }

    public HelperEating eating() { return eating; }
    public HelperPassages passages() { return passages; }
    public String workProblem() { return workGoal.problem(); }
    public HelperNightGoal nightGoal() { return nightGoal; }
    public boolean isExpedition() { return entityData.get(DATA_EXPEDITION); }
    public void setExpedition(final boolean expedition) {
        entityData.set(DATA_EXPEDITION, expedition);
        getNavigation().stop();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    /** A drop longer than a couple of blocks is never part of a planned route. */
    @Override
    public int getMaxFallDistance() {
        return MAX_FALL;
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_OWNER, Optional.empty());
        entityData.define(DATA_EXPEDITION, false);
        entityData.define(DATA_MODE, HelperMode.ROAM.id());
        entityData.define(DATA_PROFESSION, HelperProfession.NONE.id());
        entityData.define(DATA_ROAM_RADIUS, ROAM_RADII[1]);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1D, true));
        nightGoal = new HelperNightGoal(this);
        goalSelector.addGoal(2, nightGoal);
        goalSelector.addGoal(3, new dev.npchelper.work.HelperPickupGoal(this));
        workGoal = new dev.npchelper.work.HelperWorkGoal(this);
        goalSelector.addGoal(4, workGoal);
        goalSelector.addGoal(5, new HelperWanderGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new DefendOwnerGoal(this));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                (target, level) -> mayFight(target) && target.distanceToSqr(this) <= GUARD_RANGE * GUARD_RANGE));
    }

    /**
     * Whom the helper is willing to fight: never its owner, never another helper, and never a creeper of its own accord —
     * walking up to one and hitting it is how a house gets a crater in it.
     */
    public boolean mayFight(final @Nullable LivingEntity candidate) {
        if (isRetreating() || candidate == null || candidate == this || !candidate.isAlive()) return false;
        if (candidate instanceof NpcHelperEntity) return false;
        if (candidate instanceof Player player) return !isOwnedBy(player);
        return !(candidate instanceof net.minecraft.world.entity.monster.Creeper);
    }

    /** Right-click with an empty hand opens the helper's own interface. */
    @Override
    public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        if (!player.getItemInHand(hand).isEmpty()) {
            if (level().isClientSide() && getHealth() < getMaxHealth()
                    && player.getItemInHand(hand).has(net.minecraft.core.component.DataComponents.FOOD)) return InteractionResult.SUCCESS;
            if (eating.feed(player, hand)) return InteractionResult.SUCCESS;
            return super.mobInteract(player, hand);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider((containerId, inventory, ignored) -> new HelperMenu(containerId, inventory, this),
                            getDisplayName()),
                    buffer -> buffer.writeVarInt(getId()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        super.customServerAiStep(level);
        if (retreatTicks > 0) retreatTicks--;
        pickup.tick();
        eating.tick();
        passages.tick();
        // A cancelled job may leave a scaffold but no queue. Descend before night travel can take over.
        if (queue.isEmpty() && supports.any() && nightGoal.wanted()) {
            queue.set(java.util.List.of(new dev.npchelper.work.HelperStep(
                    dev.npchelper.work.HelperStep.RECOVER_SUPPORTS, "", 1)));
        }

        // A trade with nothing left in the queue takes a breath and then looks for the next job of its own.
        if (queue.isEmpty() && (getProfession() != HelperProfession.NONE || supports.any() || placedBench != null)
                && getTarget() == null && !mustReturnToOwner() && !nightGoal.wanted()) {
            if (++restTicks > REST_TICKS) {
                restTicks = 0;
                queue.set(dev.npchelper.work.HelperTrades.planFor(this, getProfession()));
            }
        } else {
            restTicks = 0;
        }

        LivingEntity target = getTarget();
        if (target == null) return;

        // Hurt badly: break off and keep to the owner until it has had a moment.
        if (isRetreating() || getHealth() < getMaxHealth() * RETREAT_HEALTH) {
            setTarget(null);
            if (!isRetreating()) retreatTicks = RETREAT_TICKS;
            return;
        }
        // A chase has a limit, or one skeleton walks the helper out of the world.
        Vec3 centre = centre();
        if (centre != null && target.position().distanceTo(centre) > CHASE_LEASH) setTarget(null);
    }

    /** Where the helper belongs right now: the owner while following or retreating, the remembered spot otherwise. */
    public @Nullable Vec3 centre() {
        if (getMode() == HelperMode.FOLLOW || isRetreating()) {
            LivingEntity owner = getOwner();
            return owner == null || owner.level() != level() ? null : owner.position();
        }
        return Vec3.atBottomCenterOf(anchor);
    }

    /**
     * Where the trade's work belongs. A sign of its own mark outranks the spot the player last sent it to: that is how a
     * grove, a mine or a field stays where it was put, however the helper has wandered since.
     */
    public @Nullable Vec3 workCentre() {
        var mark = dev.npchelper.work.HelperTrades.markFor(getProfession());
        if (mark != null && level() instanceof ServerLevel server) {
            BlockPos sign = dev.npchelper.work.HelperSigns.nearest(server, blockPosition(), workRadius() + 32, mark);
            if (sign != null) return Vec3.atBottomCenterOf(sign);
        }
        return centre();
    }

    /** Movement to the owner takes priority over work and collecting drops. */
    public boolean mustReturnToOwner() {
        if (isRetreating()) return true;
        Vec3 centre = centre();
        return getMode() == HelperMode.FOLLOW
                && (centre == null || position().distanceTo(centre) > getRoamRadius());
    }

    public boolean isRetreating() {
        return retreatTicks > 0;
    }

    public int carriedLogs() {
        int count = 0;
        for (ItemStack stack : bag.getItems()) if (stack.is(net.minecraft.tags.ItemTags.LOGS)) count += stack.getCount();
        return count;
    }

    public SimpleContainer getBag() {
        return bag;
    }

    public dev.npchelper.work.HelperPickup pickup() {
        return pickup;
    }

    public dev.npchelper.work.HelperBreaking breaking() {
        return breaking;
    }

    public dev.npchelper.work.HelperQueue queue() {
        return queue;
    }

    public dev.npchelper.work.HelperSupports supports() {
        return supports;
    }

    public @Nullable BlockPos placedBench() {
        return placedBench;
    }

    public void setPlacedBench(final @Nullable BlockPos pos) {
        placedBench = pos == null ? null : pos.immutable();
    }

    /** Puts the item in the main hand, swapping whatever was there back into the bag. */
    public void takeInHand(final net.minecraft.world.item.Item item) {
        ItemStack held = getMainHandItem();
        if (held.is(item)) return;
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            ItemStack stack = bag.getItem(slot);
            if (!stack.is(item)) continue;
            bag.setItem(slot, held.copy());
            equipFromPlayer(EquipmentSlot.MAINHAND, stack.copy());
            return;
        }
    }

    /** How far from its centre the helper goes. The player's setting, identical in every movement mode. */
    public int workRadius() {
        return getRoamRadius();
    }

    /** Gear handed over by the player is never lost silently: it drops in full when the helper dies. */
    public void equipFromPlayer(final EquipmentSlot slot, final ItemStack stack) {
        setItemSlot(slot, stack);
        setDropChance(slot, 1.0F);
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getOwnerReference() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwner(final LivingEntity owner) {
        if (getOwnerReference() == null) anchor = blockPosition();
        entityData.set(DATA_OWNER, Optional.ofNullable(EntityReference.of(owner)));
    }

    public boolean isOwnedBy(final Player player) {
        EntityReference<LivingEntity> owner = getOwnerReference();
        return owner == null || owner.getUUID().equals(player.getUUID());
    }

    public HelperMode getMode() {
        return HelperMode.byId(entityData.get(DATA_MODE));
    }

    /** Switching mode remembers where it happened, so ROAM and STAY keep to the spot the player chose. */
    public void setMode(final HelperMode mode) {
        entityData.set(DATA_MODE, mode.id());
        anchor = blockPosition();
        getNavigation().stop();
        breaking.reset();
        workGoal.resetTask();
        queue.clear();
        restTicks = 0;
    }

    public BlockPos getAnchor() {
        return anchor;
    }

    public int getRoamRadius() {
        return entityData.get(DATA_ROAM_RADIUS);
    }

    public void setRoamRadius(final int radius) {
        for (int allowed : ROAM_RADII) {
            if (allowed == radius) {
                entityData.set(DATA_ROAM_RADIUS, radius);
                return;
            }
        }
    }

    /** Steps to the next radius, back to the first after the last. */
    public void cycleRoamRadius() {
        int current = getRoamRadius();
        for (int index = 0; index < ROAM_RADII.length; index++) {
            if (ROAM_RADII[index] == current) {
                entityData.set(DATA_ROAM_RADIUS, ROAM_RADII[(index + 1) % ROAM_RADII.length]);
                return;
            }
        }
        entityData.set(DATA_ROAM_RADIUS, ROAM_RADII[0]);
    }

    /** Steps to the next trade, round to «без діла» after the last. */
    public void cycleProfession() {
        HelperProfession[] all = HelperProfession.values();
        setProfession(all[(getProfession().ordinal() + 1) % all.length]);
    }

    /** An empty name clears the custom one and goes back to «Помічник». */
    public void rename(final String name) {
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            setCustomName(null);
            setCustomNameVisible(false);
            return;
        }
        setCustomName(Component.literal(trimmed));
        setCustomNameVisible(true);
    }

    public HelperProfession getProfession() {
        return HelperProfession.byId(entityData.get(DATA_PROFESSION));
    }

    /** Taking up a trade drops whatever was being done and starts that trade's own work. */
    public void setProfession(final HelperProfession profession) {
        entityData.set(DATA_PROFESSION, profession.id());
        queue().clear();
        breaking.reset();
        getNavigation().stop();
        workGoal.resetTask();
        queue().set(dev.npchelper.work.HelperTrades.planFor(this, profession));
    }

    /** A helper is somebody's, so it stays when the player walks away. */
    @Override
    public boolean removeWhenFarAway(final double distanceToClosestPlayer) {
        return false;
    }

    /** The bag is not part of the entity's own equipment, so it is emptied out by hand. */
    @Override
    protected void dropCustomDeathLoot(final ServerLevel level, final DamageSource source, final boolean hitByPlayer) {
        super.dropCustomDeathLoot(level, source, hitByPlayer);
        for (int slot = 0; slot < bag.getContainerSize(); slot++) spawnAtLocation(level, bag.removeItemNoUpdate(slot));
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        EntityReference.store(getOwnerReference(), output, "Owner");
        output.putBoolean("Expedition", isExpedition());
        output.putString("Profession", getProfession().id());
        output.putString("Mode", getMode().id());
        output.putInt("RoamRadius", getRoamRadius());
        output.putInt("AnchorX", anchor.getX());
        output.putInt("AnchorY", anchor.getY());
        output.putInt("AnchorZ", anchor.getZ());
        ContainerHelper.saveAllItems(output.child("Bag"), bag.getItems());
        queue.store(output.child("Work"));
        supports.store(output.child("Supports"));
        passages.store(output.child("Passages"));
        workGoal.store(output.child("TreeWork"));
        if (placedBench != null) output.store("PlacedBench", BlockPos.CODEC, placedBench);
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(DATA_OWNER, Optional.ofNullable(EntityReference.read(input, "Owner")));
        entityData.set(DATA_EXPEDITION, input.getBooleanOr("Expedition", false));
        entityData.set(DATA_PROFESSION, HelperProfession.byId(input.getStringOr("Profession", "")).id());
        entityData.set(DATA_MODE, HelperMode.byId(input.getStringOr("Mode", "")).id());
        entityData.set(DATA_ROAM_RADIUS, input.getIntOr("RoamRadius", ROAM_RADII[1]));
        anchor = new BlockPos(input.getIntOr("AnchorX", 0), input.getIntOr("AnchorY", 0), input.getIntOr("AnchorZ", 0));
        if (anchor.equals(BlockPos.ZERO)) anchor = blockPosition();
        ContainerHelper.loadAllItems(input.childOrEmpty("Bag"), bag.getItems());
        queue.load(input.childOrEmpty("Work"));
        supports.load(input.childOrEmpty("Supports"));
        passages.load(input.childOrEmpty("Passages"));
        placedBench = input.read("PlacedBench", BlockPos.CODEC).orElse(null);
        workGoal.load(input.childOrEmpty("TreeWork"));
    }
}
