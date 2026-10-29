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
public final class NpcHelperEntity extends PathfinderMob implements OwnableEntity, net.minecraft.world.entity.ContainerUser {
    public static final int BAG_SIZE = 27;
    /** What the range steps through when the player clicks it. It means the same in every mode. */
    public static final int[] ROAM_RADII = {4, 8, 16, 32, 64};
    /** Hostiles further than this are somebody else's problem. */
    public static final double GUARD_RANGE = 16.0D;
    /** How far a guard keeps watch — as far as its eyes carry. */
    public static final double GUARD_SIGHT = 32.0D;
    /** Closer than this a guard hears a monster, whichever way it is facing. */
    private static final double GUARD_HEARING = 4.0D;
    /** A guard sees what is within this angle either side of where it looks. */
    private static final double GUARD_VIEW_COS = 0.5D;
    /** A monster this far below and out of the open sky is in a cave, not the guard's business. */
    private static final double UNDERGROUND = 4.0D;
    /** A chest lid stays up this long after the last item goes in or out. */
    private static final int LID_HOLD = 30;
    /** A chase that strays this far from the centre is called off. */
    private static final double CHASE_LEASH = 24.0D;
    /** Below this share of its health the helper breaks off and keeps to the owner. */
    private static final float RETREAT_HEALTH = 0.3F;
    private static final int RETREAT_TICKS = 20 * 10;
    /** Never fall further than this of its own accord. */
    private static final int MAX_FALL = 2;
    /** Getting out of a trap may cost a heart or two of a fall, but no more. */
    private static final int ESCAPE_FALL = 6;
    /** Ten seconds rooted to the spot with nothing to explain it. */
    private static final int STUCK_TICKS = 20 * 10;
    /** Shuffling on the spot is still standing still. */
    private static final double STILL_MARGIN = 0.5D;

    private @Nullable Vec3 stillAt;
    private int stillTicks;
    private boolean escaping;
    /** How often the keeper makes its round of the warehouse and the furnaces. */
    private static final int VISIT_EVERY = 20 * 60 * 3;
    /** When the next round is due; -1 until the keeper takes up its trade. */
    private long nextVisit = -1;
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
    private final SimpleContainer cargo = new SimpleContainer(1);
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
    /** The chest or barrel it has open, by its canonical position, and when it shuts again. */
    private @Nullable BlockPos openAt;
    private net.minecraft.world.@Nullable Container openContainer;
    private long lidCloses;

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
    public void cancelWorkForRescue() {
        workGoal.resetTask();
        queue.clear();
        getNavigation().stop();
    }

    public String workProblem() { return workGoal.problem(); }
    public int leavesCut() { return workGoal.leavesCut(); }
    public dev.npchelper.work.HelperWorkGoal workGoal() { return workGoal; }
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

    /** A drop longer than a couple of blocks is never part of a planned route — unless that is the only way out. */
    @Override
    public int getMaxFallDistance() {
        return escaping ? ESCAPE_FALL : MAX_FALL;
    }

    /**
     * Stuck: ten seconds without moving although nothing explains it — not fighting, not breaking a block, not climbing its
     * own pillar, and not resting for the night. Standing still for a reason is never mistaken for being trapped.
     */
    public boolean isStuck() {
        return stillTicks >= STUCK_TICKS;
    }

    public void clearStuck() {
        stillTicks = 0;
        stillAt = null;
    }

    public void setEscaping(final boolean escaping) {
        this.escaping = escaping;
    }

    /**
     * Every three minutes the keeper makes its round of the warehouse — what it gathered goes in, the furnaces are
     * emptied and loaded — whatever step its work was on. The first round is made as soon as it takes up the trade.
     */
    private void scheduleKeeperVisit(final ServerLevel level) {
        if (getProfession() != HelperProfession.KEEPER) {
            nextVisit = -1;
            return;
        }
        long now = level.getGameTime();
        if (nextVisit >= 0 && now < nextVisit) return;
        nextVisit = now + VISIT_EVERY;
        var current = queue.current();
        if (current != null && current.type().equals(dev.npchelper.work.HelperStep.KEEPER_VISIT)) return;
        workGoal.keeper().beginVisit();
        queue.prepend(java.util.List.of(new dev.npchelper.work.HelperStep(dev.npchelper.work.HelperStep.KEEPER_VISIT, "", 1)));
    }

    /** Where the keeper keeps to at night: its warehouse sign. Null by day, for other trades, or with no warehouse yet. */
    public @Nullable BlockPos nightPost() {
        if (getProfession() != HelperProfession.KEEPER || isExpedition() || !HelperNightGoal.night(this)
                || !(level() instanceof ServerLevel server)) return null;
        return workGoal.keeper().warehouse(server);
    }

    private void watchForStuck() {
        BlockPos post = nightPost();
        Vec3 home = post != null ? Vec3.atBottomCenterOf(post) : centre();
        int range = post != null ? dev.npchelper.work.HelperKeeper.NIGHT_RADIUS : workRadius();
        // Idle with nothing to do and already in place is resting, not being trapped.
        boolean wantsToMove = getNavigation().isStuck() || !queue.isEmpty()
                || home != null && position().distanceTo(home) > range + 2;
        boolean explained = !wantsToMove || getTarget() != null || nightGoal.active() || breaking.target() != null || home == null;
        if (explained || stillAt == null || position().distanceTo(stillAt) > STILL_MARGIN) {
            stillAt = position();
            stillTicks = 0;
            return;
        }
        stillTicks++;
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
        goalSelector.addGoal(2, new HelperUnstickGoal(this));
        goalSelector.addGoal(3, new dev.npchelper.work.HelperPickupGoal(this));
        workGoal = new dev.npchelper.work.HelperWorkGoal(this);
        goalSelector.addGoal(4, workGoal);
        goalSelector.addGoal(5, new HelperWanderGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new DefendOwnerGoal(this));
        targetSelector.addGoal(2, new DefendOthersGoal(this));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                (target, level) -> mayFight(target) && spots(target)));
    }

    /**
     * Whether a monster (already in line of sight) is close enough to go for. Every helper fights what comes within
     * sixteen blocks; a guard watches further, but only what is in front of its eyes and not down in a cave — it turns
     * this way and that on its round so as to see all round.
     */
    private boolean spots(final LivingEntity target) {
        double distance = target.distanceToSqr(this);
        if (getProfession() != HelperProfession.GUARD) return distance <= GUARD_RANGE * GUARD_RANGE;
        if (distance > GUARD_SIGHT * GUARD_SIGHT) return false;
        if (target.getY() < getY() - UNDERGROUND && !level().canSeeSky(target.blockPosition())) return false;
        if (distance <= GUARD_HEARING * GUARD_HEARING) return true;
        Vec3 look = getViewVector(1.0F);
        Vec3 towards = target.getEyePosition().subtract(getEyePosition());
        double flat = Math.sqrt(towards.x * towards.x + towards.z * towards.z) * Math.sqrt(look.x * look.x + look.z * look.z);
        return flat > 0 && (look.x * towards.x + look.z * towards.z) / flat >= GUARD_VIEW_COS;
    }

    /** How far from its centre a chase may go: a guard's whole range and a little over. */
    private double chaseLeash() {
        return getProfession() == HelperProfession.GUARD ? Math.max(CHASE_LEASH, workRadius() + 8.0D) : CHASE_LEASH;
    }

    /** With a shield in the other hand it goes in behind it, and lowers it to strike. */
    private void raiseShield(final @Nullable LivingEntity target) {
        boolean shield = getOffhandItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
        boolean wanted = shield && target != null && distanceTo(target) > 3.0D;
        if (wanted && !isUsingItem()) startUsingItem(InteractionHand.OFF_HAND);
        else if (!wanted && isUsingItem() && getUsedItemHand() == InteractionHand.OFF_HAND) stopUsingItem();
    }

    /**
     * Lifts the lid of the chest or barrel it is taking from or putting into, the way a player's opening it would, and
     * keeps it up while it works there. Opening another one shuts this one first.
     */
    public void openContainer(final ServerLevel level, final BlockPos pos) {
        BlockPos key = dev.npchelper.work.HelperContainers.canonical(level, pos);
        lidCloses = level.getGameTime() + LID_HOLD;
        if (key.equals(openAt)) return;
        closeContainer();
        var container = dev.npchelper.work.HelperContainers.containerAt(level, pos);
        if (container == null) return;
        openAt = key;
        openContainer = container;
        container.startOpen(this);
    }

    private void closeContainer() {
        var container = openContainer;
        openContainer = null;
        openAt = null;
        if (container != null) container.stopOpen(this);
    }

    @Override
    public boolean hasContainerOpen(final net.minecraft.world.level.block.entity.ContainerOpenersCounter counter, final BlockPos pos) {
        return openAt != null && dev.npchelper.work.HelperContainers.canonical(level(), pos).equals(openAt);
    }

    @Override
    public double getContainerInteractionRange() {
        return dev.npchelper.work.HelperBreaking.REACH;
    }

    @Override
    public void remove(final RemovalReason reason) {
        if (!level().isClientSide()) closeContainer();
        super.remove(reason);
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
        watchForStuck();
        if (tickCount % 20 == 0) scheduleKeeperVisit(level);
        if (openContainer != null && (level.getGameTime() >= lidCloses || openAt == null
                || openAt.distToCenterSqr(position()) > 36.0D)) closeContainer();
        raiseShield(getTarget());
        pickup.tick();
        eating.tick();
        passages.tick();
        nightGoal.safetyTick();
        // A cancelled job may leave a scaffold but no queue. Descend before night travel can take over.
        if (queue.isEmpty() && supports.any() && nightGoal.wanted()) {
            queue.set(java.util.List.of(new dev.npchelper.work.HelperStep(
                    dev.npchelper.work.HelperStep.RECOVER_SUPPORTS, "", 1)));
        }

        if (queue.isEmpty() && getProfession() != HelperProfession.NONE && getTarget() == null
                && !mustReturnToOwner() && !nightGoal.wanted() && !dev.npchelper.work.HelperResources.ready(this)) {
            queue.set(java.util.List.of(
                    new dev.npchelper.work.HelperStep(dev.npchelper.work.HelperStep.PREPARE_KIT, "", 1),
                    new dev.npchelper.work.HelperStep(dev.npchelper.work.HelperStep.PREPARE_TOOLS, "", 1)));
        }
        // A trade with nothing left in the queue takes a breath and then looks for the next job of its own.
        if (queue.isEmpty() && (getProfession() != HelperProfession.NONE || supports.any() || placedBench != null || !cargo.isEmpty())
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
        if (centre != null && target.position().distanceTo(centre) > chaseLeash()) setTarget(null);
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

    public SimpleContainer getCargo() { return cargo; }

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
        if (getOffhandItem().is(item)) {
            ItemStack selected = getOffhandItem().copy();
            equipFromPlayer(EquipmentSlot.OFFHAND, held.copy());
            equipFromPlayer(EquipmentSlot.MAINHAND, selected);
            return;
        }
        for (int slot = 0; slot < bag.getContainerSize(); slot++) {
            ItemStack stack = bag.getItem(slot);
            if (!stack.is(item)) continue;
            bag.setItem(slot, held.copy());
            equipFromPlayer(EquipmentSlot.MAINHAND, stack.copy());
            return;
        }
    }

    /** Puts the item in the other hand, out of the bag or the main hand; what was there goes into the bag. */
    public void takeInOffhand(final net.minecraft.world.item.Item item) {
        ItemStack held = getOffhandItem();
        if (held.is(item)) return;
        ItemStack chosen = ItemStack.EMPTY;
        if (getMainHandItem().is(item)) {
            chosen = getMainHandItem().copy();
            equipFromPlayer(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        } else {
            for (int slot = 0; slot < bag.getContainerSize() && chosen.isEmpty(); slot++) {
                if (bag.getItem(slot).is(item)) chosen = bag.removeItem(slot, 1);
            }
        }
        if (chosen.isEmpty()) return;
        equipFromPlayer(EquipmentSlot.OFFHAND, chosen);
        if (!held.isEmpty()) {
            ItemStack left = bag.addItem(held.copy());
            if (!left.isEmpty()) spawnAtLocation((ServerLevel) level(), left);
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
        spawnAtLocation(level, cargo.removeItemNoUpdate(0));
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
        ContainerHelper.saveAllItems(output.child("Cargo"), cargo.getItems());
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
        ContainerHelper.loadAllItems(input.childOrEmpty("Cargo"), cargo.getItems());
        queue.load(input.childOrEmpty("Work"));
        supports.load(input.childOrEmpty("Supports"));
        passages.load(input.childOrEmpty("Passages"));
        placedBench = input.read("PlacedBench", BlockPos.CODEC).orElse(null);
        workGoal.load(input.childOrEmpty("TreeWork"));
    }
}
