package dev.npchelper.entity;

import dev.npchelper.inventory.HelperMenu;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A crafted helper. Owner, name, mode and bag live on the helper, not on the player, so one world can hold several of them
 * and each keeps its own across a reload.
 */
public final class NpcHelperEntity extends PathfinderMob implements OwnableEntity {
    public static final int BAG_SIZE = 9;
    /** What the roaming radius steps through when the player clicks it. */
    public static final int[] ROAM_RADII = {8, 16, 32, 48};

    private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER = SynchedEntityData.defineId(
            NpcHelperEntity.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    /** Synched, so the interface shows the mode without asking the server. */
    private static final EntityDataAccessor<String> DATA_MODE = SynchedEntityData.defineId(
            NpcHelperEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_PROFESSION = SynchedEntityData.defineId(
            NpcHelperEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_ROAM_RADIUS = SynchedEntityData.defineId(
            NpcHelperEntity.class, EntityDataSerializers.INT);

    private final SimpleContainer bag = new SimpleContainer(BAG_SIZE);
    /** The spot the helper keeps to in every mode but FOLLOW. */
    private BlockPos anchor = BlockPos.ZERO;

    public NpcHelperEntity(final EntityType<? extends NpcHelperEntity> type, final Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_OWNER, Optional.empty());
        entityData.define(DATA_MODE, HelperMode.ROAM.id());
        entityData.define(DATA_PROFESSION, HelperProfession.NONE.id());
        entityData.define(DATA_ROAM_RADIUS, ROAM_RADII[1]);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new HelperWanderGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** Right-click opens the helper's own interface. */
    @Override
    public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        if (!player.getItemInHand(hand).isEmpty()) return super.mobInteract(player, hand);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider((containerId, inventory, ignored) -> new HelperMenu(containerId, inventory, this),
                            getDisplayName()),
                    buffer -> buffer.writeVarInt(getId()));
        }
        return InteractionResult.SUCCESS;
    }

    public SimpleContainer getBag() {
        return bag;
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getOwnerReference() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwner(final LivingEntity owner) {
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
    }

    public BlockPos getAnchor() {
        return anchor;
    }

    public int getRoamRadius() {
        return entityData.get(DATA_ROAM_RADIUS);
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

    public void setProfession(final HelperProfession profession) {
        entityData.set(DATA_PROFESSION, profession.id());
    }

    /** A helper is somebody's, so it stays when the player walks away. */
    @Override
    public boolean removeWhenFarAway(final double distanceToClosestPlayer) {
        return false;
    }

    /** The bag is not kept by the entity itself, so it is dropped by hand on death. */
    @Override
    protected void dropCustomDeathLoot(final net.minecraft.server.level.ServerLevel level,
                                       final net.minecraft.world.damagesource.DamageSource source, final boolean hitByPlayer) {
        super.dropCustomDeathLoot(level, source, hitByPlayer);
        for (int slot = 0; slot < bag.getContainerSize(); slot++) spawnAtLocation(level, bag.removeItemNoUpdate(slot));
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        EntityReference.store(getOwnerReference(), output, "Owner");
        output.putString("Profession", getProfession().id());
        output.putString("Mode", getMode().id());
        output.putInt("RoamRadius", getRoamRadius());
        output.putInt("AnchorX", anchor.getX());
        output.putInt("AnchorY", anchor.getY());
        output.putInt("AnchorZ", anchor.getZ());
        ContainerHelper.saveAllItems(output.child("Bag"), bag.getItems());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(DATA_OWNER, Optional.ofNullable(EntityReference.read(input, "Owner")));
        entityData.set(DATA_PROFESSION, HelperProfession.byId(input.getStringOr("Profession", "")).id());
        entityData.set(DATA_MODE, HelperMode.byId(input.getStringOr("Mode", "")).id());
        entityData.set(DATA_ROAM_RADIUS, input.getIntOr("RoamRadius", ROAM_RADII[1]));
        anchor = new BlockPos(input.getIntOr("AnchorX", 0), input.getIntOr("AnchorY", 0), input.getIntOr("AnchorZ", 0));
        if (anchor.equals(BlockPos.ZERO)) anchor = blockPosition();
        ContainerHelper.loadAllItems(input.childOrEmpty("Bag"), bag.getItems());
    }
}
