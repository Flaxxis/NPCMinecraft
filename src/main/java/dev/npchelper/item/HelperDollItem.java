package dev.npchelper.item;

import dev.npchelper.NpcHelperMod;
import dev.npchelper.entity.NpcHelperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** The crafted wooden figure. Using it on the ground stands a helper up there, owned by whoever placed it. */
public final class HelperDollItem extends Item {
    public HelperDollItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

        // One block out from the face that was clicked, so the helper never stands inside the block.
        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        NpcHelperEntity helper = NpcHelperMod.HELPER.get().spawn(serverLevel, spawnPos, EntitySpawnReason.SPAWN_ITEM_USE);
        if (helper == null) return InteractionResult.FAIL;

        Player player = context.getPlayer();
        if (player != null) helper.setOwner(player);

        // A doll renamed on an anvil hands its name to the helper.
        ItemStack stack = context.getItemInHand();
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null) helper.setCustomName(name);

        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
