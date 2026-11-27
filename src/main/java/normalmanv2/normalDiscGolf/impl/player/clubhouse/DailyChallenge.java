package normalmanv2.normalDiscGolf.impl.player.clubhouse;

import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;

import java.time.LocalDate;

public enum DailyChallenge {
    BASKET_HUNT("Basket Hunt", "Sink 5 baskets across completed rounds.", 5, 35),
    UNDER_PAR("Under Par", "Hole out under par twice across completed rounds.", 2, 45),
    BACK_FOR_MORE("Back for More", "Finish 2 rounds with at least one basket each.", 2, 30);

    private final String title;
    private final String description;
    private final int target;
    private final int credits;

    DailyChallenge(String title, String description, int target, int credits) {
        this.title = title;
        this.description = description;
        this.target = target;
        this.credits = credits;
    }

    public static DailyChallenge forDay(LocalDate day) {
        return values()[Math.floorMod(day.toEpochDay(), values().length)];
    }

    int contribution(ScoreCard card) {
        return switch (this) {
            case BASKET_HUNT -> card.getHoledOutCount();
            case UNDER_PAR -> (int) card.getBaskets().values().stream().filter(ScoreCard.Basket::underPar).count();
            case BACK_FOR_MORE -> 1;
        };
    }

    public String title() { return title; }
    public String description() { return description; }
    public int target() { return target; }
    public int credits() { return credits; }
}
