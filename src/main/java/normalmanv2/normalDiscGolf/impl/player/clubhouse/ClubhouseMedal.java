package normalmanv2.normalDiscGolf.impl.player.clubhouse;

public enum ClubhouseMedal {
    FIRST_CHAINS("First Chains", "Sink your first basket in a completed round.", 15),
    HOT_HAND("Hot Hand", "Finish a round with a streak of 3 baskets.", 20),
    ACE_CARD("Ace Card", "Hole out in a single throw.", 50),
    DISTANCE_MAKER("Distance Maker", "Sink a throw from at least 40 blocks.", 40),
    PARKLAND_REGULAR("Parkland Regular", "Sink 25 baskets in completed rounds.", 60),
    CLEAN_NINE("Clean Nine", "Hole out on every hole of a round with at least 9 holes.", 50);

    private final String title;
    private final String description;
    private final int credits;

    ClubhouseMedal(String title, String description, int credits) {
        this.title = title;
        this.description = description;
        this.credits = credits;
    }

    public String title() { return title; }
    public String description() { return description; }
    public int credits() { return credits; }
}
