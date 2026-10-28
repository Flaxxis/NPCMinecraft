package dev.npchelper.work;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The steps a helper is working through, one at a time. The queue survives a reload, and any order of the player — a change
 * of mode, of profession, or a fight — drops it, so an old plan never wakes up behind the player's back.
 */
public final class HelperQueue {
    /** A step that has got nowhere in this long is given up, and the rest of the queue with it. */
    public static final int STEP_LIMIT_TICKS = 20 * 90;

    private final Deque<HelperStep> steps = new ArrayDeque<>();
    private int ticksOnStep;

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public @Nullable HelperStep current() {
        return steps.peekFirst();
    }

    public void set(final List<HelperStep> plan) {
        clear();
        steps.addAll(plan);
    }

    public void clear() {
        steps.clear();
        ticksOnStep = 0;
    }

    /** The current step is done; the next one starts with a fresh clock. */
    public void advance() {
        steps.pollFirst();
        ticksOnStep = 0;
    }

    /** True while the step is still worth working on. */
    public boolean tick() {
        if (steps.isEmpty()) return false;
        if (++ticksOnStep <= STEP_LIMIT_TICKS) return true;
        clear();
        return false;
    }

    /** Progress resets the clock: real work is never cut off for taking a while. */
    public void noteProgress() {
        ticksOnStep = 0;
    }

    public void store(final ValueOutput output) {
        if (steps.isEmpty()) return;
        var list = output.childrenList("Steps");
        for (HelperStep step : steps) step.store(list.addChild());
    }

    public void load(final ValueInput input) {
        clear();
        for (ValueInput child : input.childrenListOrEmpty("Steps")) steps.addLast(HelperStep.load(child));
    }

    @Override
    public String toString() {
        HelperStep current = current();
        return current == null ? "idle" : current + " (+" + Math.max(0, steps.size() - 1) + ")";
    }
}
