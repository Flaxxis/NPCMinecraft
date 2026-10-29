package dev.npchelper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.npchelper.entity.HelperSkins;
import dev.npchelper.entity.NpcHelperEntity;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * The helper looks like a player: the player's own model, outer layer and all, wearing whichever default player skin it was
 * given, with wide or slim arms to match.
 */
public final class HelperRenderer extends MobRenderer<NpcHelperEntity, AvatarRenderState, PlayerModel> {
    private static final List<PlayerSkin> SKINS = HelperSkins.ALL.stream()
            .map(skin -> new PlayerSkin(new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace(skin.texture())), null, null,
                    skin.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE, true))
            .toList();

    private final PlayerModel wide;
    private final PlayerModel slim;

    public HelperRenderer(final EntityRendererProvider.Context context) {
        super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        wide = model;
        slim = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        // Armour the player hands over has to show on the helper.
        addLayer(new HumanoidArmorLayer<>(this,
                ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), part -> new PlayerModel(part, false)),
                context.getEquipmentRenderer()));
        addLayer(new ItemInHandLayer<>(this));
        addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache()));
        addLayer(new WingsLayer<>(this, context.getModelSet(), context.getEquipmentRenderer()));
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(final NpcHelperEntity helper, final AvatarRenderState state, final float partialTicks) {
        super.extractRenderState(helper, state, partialTicks);
        HumanoidMobRenderer.extractHumanoidRenderState(helper, state, partialTicks, itemModelResolver);
        state.leftArmPose = armPose(helper, HumanoidArm.LEFT);
        state.rightArmPose = armPose(helper, HumanoidArm.RIGHT);
        state.skin = SKINS.get(helper.getSkinIndex());
    }

    /** An arm holding something is raised a little, as a player's is; a shield in use is held up. */
    private static HumanoidModel.ArmPose armPose(final NpcHelperEntity helper, final HumanoidArm arm) {
        InteractionHand hand = arm == helper.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack held = helper.getItemInHand(hand);
        if (held.isEmpty()) return HumanoidModel.ArmPose.EMPTY;
        if (helper.isUsingItem() && helper.getUsedItemHand() == hand && held.getUseAnimation() == ItemUseAnimation.BLOCK) {
            return HumanoidModel.ArmPose.BLOCK;
        }
        return HumanoidMobRenderer.usesSpearPose(held, arm, helper) ? HumanoidModel.ArmPose.SPEAR : HumanoidModel.ArmPose.ITEM;
    }

    @Override
    public void submit(final AvatarRenderState state, final PoseStack poseStack, final SubmitNodeCollector collector, final CameraRenderState camera) {
        model = state.skin.model() == PlayerModelType.SLIM ? slim : wide;
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(final AvatarRenderState state) {
        return state.skin.body().texturePath();
    }

    /** Players are drawn a touch smaller than their model; so is the helper, to stand as tall as one. */
    @Override
    protected void scale(final AvatarRenderState state, final PoseStack poseStack) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }
}
