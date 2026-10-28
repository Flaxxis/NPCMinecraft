package dev.npchelper.net;

/** The name the player typed for a helper. */
public record RenameHelper(int entityId, String name) {
    public static final int MAX_LENGTH = 32;
}
