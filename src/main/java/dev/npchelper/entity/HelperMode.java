package dev.npchelper.entity;

import java.util.Locale;

/**
 * What a helper is doing. Every mode is the same movement — wander around a centre within a radius — and differs only in
 * where that centre is. How far it may stray is the helper's own setting, the same whichever mode it is in.
 */
public enum HelperMode {
    /** Around the spot remembered when the order was given. */
    ROAM,
    /** The same spot, but kept to: it stands about rather than ranging. */
    STAY,
    /** Around the owner, who moves. */
    FOLLOW;

    /** Never closer in than this, so following does not mean treading on the owner's heels. */
    public static final int MIN_DISTANCE = 2;
    /** Staying put means a quarter of the chosen range, and never more than a few blocks of drift. */
    public static final double STAY_SHARE = 0.25D;

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
