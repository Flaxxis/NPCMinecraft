package dev.npchelper.net;

import dev.npchelper.NpcHelperMod;
import dev.npchelper.entity.NpcHelperEntity;
import net.minecraft.resources.Identifier;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.SimpleChannel;

/**
 * One message: the name typed in the helper's interface. The mode buttons ride the vanilla menu-button packet, so nothing
 * else needs a channel of its own.
 */
public final class HelperNetwork {
    private static final int PROTOCOL = 1;

    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(Identifier.fromNamespaceAndPath(NpcHelperMod.MODID, "main"))
            .networkProtocolVersion(PROTOCOL)
            .simpleChannel();

    private HelperNetwork() { }

    public static void register() {
        CHANNEL.messageBuilder(RenameHelper.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> {
                    buffer.writeVarInt(message.entityId());
                    buffer.writeUtf(message.name(), RenameHelper.MAX_LENGTH);
                })
                .decoder(buffer -> new RenameHelper(buffer.readVarInt(), buffer.readUtf(RenameHelper.MAX_LENGTH)))
                .consumerMainThread((message, context) -> {
                    var sender = context.getSender();
                    if (sender == null) return;
                    // The name comes from the client, so it is checked here and nowhere else.
                    if (sender.level().getEntity(message.entityId()) instanceof NpcHelperEntity helper
                            && helper.isOwnedBy(sender) && helper.distanceTo(sender) < 8.0F) {
                        helper.rename(message.name());
                    }
                })
                .add();
    }
}
