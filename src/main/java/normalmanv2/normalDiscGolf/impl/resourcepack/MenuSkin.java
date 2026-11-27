package normalmanv2.normalDiscGolf.impl.resourcepack;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Locale;

public enum MenuSkin {
    ROUNDS("§4§lRound Picker", 3, 0x69d2b1),
    LOBBIES("§4§lCourse Lobbies", 6, 0x7ac9ab),
    CLUBHOUSE("Parkland Clubhouse", 6, 0xc8d284),
    SHOP("Parkland Pro Shop", 6, 0xf4bd62),
    BAG("Your Disc Bag", 6, 0x83c5f0),
    ROUND_BAG("Round Disc Bag", 3, 0x83c5f0),
    PRACTICE("Putting Green", 6, 0xa5e49a),
    DECALS("Score Decals", 3, 0xe6abe6),
    GUIDE("Disc Golf Guide", 4, 0x98d6de),
    PARTY("§6Party", 3, 0xe2ab86),
    SCORECARD("Round Scorecard", 6, 0x94dac0),
    SUMMARY("Round Summary", 4, 0xf4c46d);

    static final char LEFT = '\ue720';
    static final char LABEL = '\ue721';
    private final String title;
    private final int rows;
    private final int accent;

    MenuSkin(String title, int rows, int accent) {
        this.title = title;
        this.rows = rows;
        this.accent = accent;
    }

    public int slots() { return rows * 9; }
    int rows() { return rows; }
    int accent() { return accent; }
    String id() { return name().toLowerCase(Locale.ROOT); }
    char glyph() { return (char) (0xe730 + ordinal()); }

    public String title(boolean textured) {
        if (!textured) return title;
        return "§f" + LEFT + glyph() + LABEL + "§f§l" + ChatColor.stripColor(title);
    }

    public static boolean available(Player player) {
        try {
            return NDGManager.getInstance().getPacked().texturedMenus(player);
        } catch (IOException unavailable) {
            return false;
        }
    }
}
