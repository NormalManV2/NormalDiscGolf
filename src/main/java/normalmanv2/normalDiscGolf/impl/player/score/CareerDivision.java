package normalmanv2.normalDiscGolf.impl.player.score;

public enum CareerDivision {
    MA4(0, "MA4 — Novice"),
    MA3(800, "MA3 — Recreational"),
    MA2(850, "MA2 — Intermediate"),
    MA1(900, "MA1 — Advanced"),
    MPO(950, "MPO — Professional"),
    ELITE(1000, "Elite Professional");

    private final int minimum;
    private final String label;
    private static final CareerDivision[] DIVISIONS = values();

    CareerDivision(int minimum, String label) {
        this.minimum = minimum;
        this.label = label;
    }

    public int minimum() { return minimum; }
    public String label() { return label; }

    public CareerDivision next() {
        return this == ELITE ? null : DIVISIONS[ordinal() + 1];
    }

    public static CareerDivision forRating(int rating) {
        for (int i = DIVISIONS.length - 1; i > 0; i--)
            if (rating >= DIVISIONS[i].minimum) return DIVISIONS[i];
        return MA4;
    }
}
