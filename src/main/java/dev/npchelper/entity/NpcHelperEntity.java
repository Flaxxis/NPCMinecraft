package dev.npchelper.entity;

import java.util.Optional;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A crafted helper. Owner, name and profession live on the helper, not on the player, so one world can hold several of them
 * and each keeps its own across a reload.
 */
public final class NpcHelperEntity extends PathfinderMob implements OwnableEntity {
    private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER = SynchedEntityData.defineId(
            NpcHelperEntity.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    /** Synched so the interface can show it without asking the server. */
    private static final EntityDataAccessor<String> DATA_PROFESSION = SynchedEntityData.defineId(
            NpcHelperEntity.class, EntityDataSerializers.STRING);

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
        entityData.define(DATA_PROFESSION, HelperProfession.NONE.id());
    }

    /** Standing about and looking around. Orders and work come with the behaviour commands. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getOwnerReference() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwner(final LivingEntity owner) {
        entityData.set(DATA_OWNER, Optional.ofNullable(EntityReference.of(owner)));
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

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        EntityReference.store(getOwnerReference(), output, "Owner");
        output.putString("Profession", getProfession().id());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        EntityReference<LivingEntity> owner = EntityReference.read(input, "Owner");
        entityData.set(DATA_OWNER, Optional.ofNullable(owner));
        entityData.set(DATA_PROFESSION, HelperProfession.byId(input.getStringOr("Profession", "")).id());
    }
}
