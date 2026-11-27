package normalmanv2.normalDiscGolf.impl.resourcepack;

public enum RoundHudSkin {
    BANNER, HOLE, DISTANCE, STROKES, SCORE, TURN, TIME, STANDINGS;

    char glyph() { return (char) (0xe750 + ordinal()); }

    public String label(String text) { return "§f" + glyph() + " " + text; }
}
