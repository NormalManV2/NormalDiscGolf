package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.round.GameRound;

import java.util.Locale;

public enum RoundFormat {
    FFA("ffa", "FFA"), DOUBLES("doubles", "Doubles"), MATCH("match", "Match Play"),
    BEST_SHOT("best-shot", "Best Shot Doubles"), WORST_SHOT("worst-shot", "Worst Shot Doubles"),
    ONE_DISC("one-disc", "One Disc Challenge"), DISC_ROULETTE("disc-roulette", "Disc Roulette"),
    ELIMINATION("elimination", "Elimination"), ACE_RACE("ace-race", "Ace Race"),
    CLOSEST_TO_PIN("closest-to-pin", "Closest to Pin"), ARCADE("arcade", "Arcade"), DAILY("daily", "Daily Course"),
    WEEKLY("weekly", "Weekly Tournament");

    private final String id;
    private final String label;

    RoundFormat(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }
    public boolean usesPairedAttempts() { return this == BEST_SHOT || this == WORST_SHOT; }
    public boolean usesDoublesTeams() { return this == DOUBLES || usesPairedAttempts(); }
    public boolean usesIndividualTeams() { return !usesDoublesTeams() && this != MATCH; }
    public boolean countsForStrokeRating() { return this == FFA || this == DOUBLES; }
    public boolean canQueueDirectly() { return this != WEEKLY; }

    public static RoundFormat parse(String id) {
        if (id == null) return null;
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "ffa" -> FFA;
            case "doubles" -> DOUBLES;
            case "match", "match-play", "matchplay" -> MATCH;
            case "best-shot", "best_shot", "bestshot" -> BEST_SHOT;
            case "worst-shot", "worst_shot", "worstshot" -> WORST_SHOT;
            case "one-disc", "one_disc", "onedisc" -> ONE_DISC;
            case "disc-roulette", "disc_roulette", "discroulette", "roulette" -> DISC_ROULETTE;
            case "elimination" -> ELIMINATION;
            case "ace-race", "ace_race", "acerace" -> ACE_RACE;
            case "closest-to-pin", "closest_to_pin", "closesttopin", "ctp" -> CLOSEST_TO_PIN;
            case "arcade" -> ARCADE;
            case "daily", "daily-course" -> DAILY;
            case "weekly", "weekly-tournament" -> WEEKLY;
            default -> null;
        };
    }

    public static RoundFormat of(GameRound round) {
        if (round instanceof MatchPlayRound) return MATCH;
        if (round instanceof BestShotRound) return BEST_SHOT;
        if (round instanceof WorstShotRound) return WORST_SHOT;
        if (round instanceof OneDiscRound) return ONE_DISC;
        if (round instanceof DiscRouletteRound) return DISC_ROULETTE;
        if (round instanceof EliminationRound) return ELIMINATION;
        if (round instanceof AceRaceRound) return ACE_RACE;
        if (round instanceof ClosestToPinRound) return CLOSEST_TO_PIN;
        if (round instanceof ArcadeRound) return ARCADE;
        if (round instanceof DailyRound) return DAILY;
        if (round instanceof WeeklyTournamentRound) return WEEKLY;
        if (round instanceof DoublesRound) return DOUBLES;
        return round instanceof FFARound ? FFA : null;
    }
}
