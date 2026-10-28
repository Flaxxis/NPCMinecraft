package dev.npchelper.net;

import dev.npchelper.work.HelperStep;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/** The authoritative queue of the helper in one open menu. */
public record HelperActions(int containerId, int entityId, String status, List<HelperStep.View> steps) {
    public HelperActions { steps = List.copyOf(steps); }

    public static void encode(final HelperActions message, final FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.containerId());
        buffer.writeVarInt(message.entityId());
        buffer.writeUtf(message.status(), 256);
        buffer.writeVarInt(message.steps().size());
        for (var step : message.steps()) {
            buffer.writeUtf(step.type(), 64);
            buffer.writeUtf(step.item(), 256);
            buffer.writeVarInt(step.count());
            buffer.writeVarInt(step.done());
        }
    }

    public static HelperActions decode(final FriendlyByteBuf buffer) {
        int menu = buffer.readVarInt(), entity = buffer.readVarInt();
        String status = buffer.readUtf(256);
        int size = buffer.readVarInt();
        if (size < 0 || size > 1024) throw new IllegalArgumentException("Invalid helper queue size: " + size);
        var steps = new java.util.ArrayList<HelperStep.View>(size);
        for (int index = 0; index < size; index++) {
            steps.add(new HelperStep.View(buffer.readUtf(64), buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt()));
        }
        return new HelperActions(menu, entity, status, steps);
    }
}
