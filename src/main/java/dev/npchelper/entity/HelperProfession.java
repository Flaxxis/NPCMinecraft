package dev.npchelper.entity;

import java.util.Locale;

/** What a helper is set to do. Stored on the helper itself, so every helper in a world keeps its own. */
public enum HelperProfession {
    /** Freshly made: takes orders, picks no work on its own. */
    NONE,
    GUARD,
    LUMBERJACK,
    MINER,
    SMELTER,
    HUNTER,
    BUILDER,
    KEEPER;

    /** The value saved in NBT. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether the trade actually does its work yet. The others are names held in reserve — kept here so saved helpers and
     * their data keep their place — and are offered to the player only once they are built.
     */
    public boolean available() {
        return this == NONE || this == LUMBERJACK || this == KEEPER;
    }

    /** The profession for a saved id; NONE for anything unknown, so an old or edited save still loads. */
    public static HelperProfession byId(String id) {
        for (HelperProfession profession : values()) {
            if (profession.id().equals(id)) return profession;
        }
        return NONE;
    }
}
