package dev.npchelper.client;

import dev.npchelper.entity.NpcHelperEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** The helper is a plain humanoid; the vanilla humanoid layer carries the shape and the default player skin dresses it. */
public final class HelperRenderer extends HumanoidMobRenderer<NpcHelperEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    // Wide arms, to match the humanoid layer below.
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

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
