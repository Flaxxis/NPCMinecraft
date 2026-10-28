package dev.npchelper.work;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** One piece of work in the queue. What counts as done is measured in the world, never by a method returning true. */
public final class HelperStep {
    /** Fell logs until the count is in the bag. */
    public static final String GATHER_WOOD = "gather_wood";
    /** Make the item at a bench, setting one down and taking it back when none stands nearby. */
    public static final String CRAFT = "craft";
    /** Take the item out of a chest or barrel nearby. */
    public static final String FETCH = "fetch";
    /** Put the item in hand. */
    public static final String EQUIP = "equip";
    /** Break the bench that was set down for the work and take it along. */
    public static final String TAKE_BENCH = "take_bench";
    /** Dig soil until the count is in the bag, for the steps the work needs. */
    public static final String GATHER_DIRT = "gather_dirt";
    /** Put a sapling in where the trunk stood. */
    public static final String PLANT = "plant";
    /** Carry what was gathered to a chest or barrel and leave it there. */
    public static final String STORE = "store";

    private final String type;
    private final String item;
    private final int count;
    private int done;

    public HelperStep(final String type, final String item, final int count) {
        this.type = type;
        this.item = item;
        this.count = Math.max(1, count);
    }

    public String type() {
        return type;
    }

    public String item() {
        return item;
    }

    public int count() {
        return count;
    }

    public int done() {
        return done;
    }

    public void progress() {
        done++;
    }

    public boolean finished() {
        return done >= count;
    }

    public void store(final ValueOutput output) {
        output.putString("Type", type);
        output.putString("Item", item);
        output.putInt("Count", count);
        output.putInt("Done", done);
    }

    public static HelperStep load(final ValueInput input) {
        HelperStep step = new HelperStep(
                input.getStringOr("Type", GATHER_WOOD),
                input.getStringOr("Item", ""),
                input.getIntOr("Count", 1));
        step.done = input.getIntOr("Done", 0);
        return step;
    }

    @Override
    public String toString() {
        return type + (item.isEmpty() ? "" : " " + item) + " " + done + "/" + count;
    }
}
