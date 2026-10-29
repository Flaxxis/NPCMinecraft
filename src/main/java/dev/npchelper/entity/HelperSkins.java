package dev.npchelper.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * The looks a helper can be given: the game's own nine default player skins, each with wide or slim arms. The helper
 * keeps only the number; the client turns it into a texture and the matching arm shape.
 */
public final class HelperSkins {
    /** One look: the texture under {@code textures/}, without the extension, and whether the arms are slim. */
    public record Skin(String label, String texture, boolean slim) { }

    private static final String[][] NAMES = {
            {"steve", "Стів"}, {"alex", "Алекс"}, {"ari", "Арі"}, {"efe", "Ефе"}, {"kai", "Кай"},
            {"makena", "Макена"}, {"noor", "Нур"}, {"sunny", "Санні"}, {"zuri", "Зурі"}};

    public static final List<Skin> ALL;
    /** Steve with wide arms: how every helper looked before skins could be chosen. */
    public static final int DEFAULT = 0;

    static {
        List<Skin> all = new ArrayList<>();
        for (String[] name : NAMES) {
            all.add(new Skin(name[1] + ", широкі", "entity/player/wide/" + name[0], false));
            all.add(new Skin(name[1] + ", тонкі", "entity/player/slim/" + name[0], true));
        }
        ALL = List.copyOf(all);
    }

    private HelperSkins() { }

    /** Any number gives a skin: out-of-range values from an old or edited save wrap round. */
    public static Skin get(final int index) {
        return ALL.get(Math.floorMod(index, ALL.size()));
    }

    public static int next(final int index, final int step) {
        return Math.floorMod(index + step, ALL.size());
    }
}
