package dev.npchelper.entity;

import java.util.Locale;

/**
 * What a helper is doing. Every mode is the same movement — wander around a centre within a radius — and differs only in
 * where that centre is and how far it may stray.
 */
public enum HelperMode {
    /** Around the spot remembered when the order was given, as far as the chosen radius. */
    ROAM,
    /** The same, kept to a couple of blocks: standing about rather than standing still. */
    STAY,
    /** Around the owner, who moves. */
    FOLLOW;

    /** Blocks from the centre; ROAM uses the helper's own setting instead. */
    public static final int STAY_RADIUS = 3;
    public static final int FOLLOW_MIN = 2, FOLLOW_MAX = 10;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static HelperMode byId(String id) {
        for (HelperMode mode : values()) {
            if (mode.id().equals(id)) return mode;
        }
        return ROAM;
    }
}
