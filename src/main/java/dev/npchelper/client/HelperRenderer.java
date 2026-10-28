package dev.npchelper.client;

import dev.npchelper.NpcHelperMod;
import dev.npchelper.entity.NpcHelperEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** The helper is a plain humanoid in a wooden skin; the vanilla humanoid layer carries the shape. */
public final class HelperRenderer extends HumanoidMobRenderer<NpcHelperEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(NpcHelperMod.MODID, "textures/entity/helper.png");

    public HelperRenderer(final EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(final HumanoidRenderState state) {
        return TEXTURE;
    }
}
