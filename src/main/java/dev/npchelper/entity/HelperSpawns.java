package dev.npchelper.entity;

import dev.npchelper.NpcHelperMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * The first helper, handed out once per player so a fresh world has somebody to try things on. The flag in the player's
 * persistent data makes it once and for all: a helper that is lost or dismissed is crafted again, not granted again.
 */
public final class HelperSpawns {
    private static final String STARTER_GIVEN = "npchelper:starter_given";

    private HelperSpawns() { }

    public static void onPlayerLoggedIn(final ServerPlayer player) {
        var data = player.getPersistentData();
        if (data.getBooleanOr(STARTER_GIVEN, false)) return;
        data.putBoolean(STARTER_GIVEN, true);

        if (!(player.level() instanceof ServerLevel level)) return;
        BlockPos pos = player.blockPosition().relative(player.getDirection(), 2);
        NpcHelperEntity helper = NpcHelperMod.HELPER.get().spawn(level, pos, EntitySpawnReason.MOB_SUMMONED);
        if (helper == null) return;

        helper.setOwner(player);
        player.sendSystemMessage(Component.literal("Помічник чекає на тебе поруч. Перейменувати його можна бляхою з іменем."));
    }
}
