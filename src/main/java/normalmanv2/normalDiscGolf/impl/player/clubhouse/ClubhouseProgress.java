package normalmanv2.normalDiscGolf.impl.player.clubhouse;

import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Applies progress from a completed scorecard once, at the same time as the round payout. */
public record ClubhouseProgress(long baskets, long underPar, long aces, double longestBasket, int bestStreak,
                                long qualifyingRounds, Map<String, Integer> bestCards, Set<ClubhouseMedal> medals,
                                LocalDate dailyDate, int dailyCount) {
    public ClubhouseProgress {
        bestCards = Map.copyOf(bestCards);
        medals = Set.copyOf(medals);
        dailyCount = Math.max(0, dailyCount);
    }

    public ClubhouseProgress() {
        this(0, 0, 0, 0, 0, 0, Map.of(), Set.of(), null, 0);
    }

    public record Result(ClubhouseProgress progress, int streakCredits, int dailyCredits,
                         List<ClubhouseMedal> newMedals, boolean bestCard) {
        public int bonusCredits() {
            return streakCredits + dailyCredits + newMedals.stream().mapToInt(ClubhouseMedal::credits).sum();
        }
    }

    public int dailyProgress(LocalDate day) {
        return day.equals(dailyDate) ? Math.min(dailyCount, DailyChallenge.forDay(day).target()) : 0;
    }

    public Result recordRound(ScoreCard card, UUID player, String format, int holes, LocalDate day) {
        if (card.getHoleScores().size() != holes || card.getHoledOutCount() == 0 || !card.participated(player))
            return new Result(this, 0, 0, List.of(), false);

        long totalBaskets = baskets;
        long totalUnderPar = underPar;
        long totalAces = aces;
        double longest = longestBasket;
        for (ScoreCard.Basket basket : card.getBaskets().values()) {
            if (!player.equals(basket.player())) continue;
            totalBaskets++;
            if (basket.underPar()) totalUnderPar++;
            if (basket.ace()) totalAces++;
            if (Double.isFinite(basket.distance())) longest = Math.max(longest, basket.distance());
        }

        int streak = card.getBestBasketStreak();
        boolean cleanCard = card.getHoledOutCount() == holes;
        var cards = new HashMap<>(bestCards);
        String key = format + "-" + holes;
        int score = card.getTotalScore();
        boolean personalBest = cleanCard && (cards.get(key) == null || score < cards.get(key));
        if (personalBest) cards.put(key, score);

        var earned = EnumSet.noneOf(ClubhouseMedal.class);
        if (totalBaskets > 0) earned.add(ClubhouseMedal.FIRST_CHAINS);
        if (streak >= 3) earned.add(ClubhouseMedal.HOT_HAND);
        if (totalAces > 0) earned.add(ClubhouseMedal.ACE_CARD);
        if (longest >= 40) earned.add(ClubhouseMedal.DISTANCE_MAKER);
        if (totalBaskets >= 25) earned.add(ClubhouseMedal.PARKLAND_REGULAR);
        if (cleanCard && holes >= 9) earned.add(ClubhouseMedal.CLEAN_NINE);
        var fresh = new ArrayList<ClubhouseMedal>();
        for (ClubhouseMedal medal : earned) if (!medals.contains(medal)) fresh.add(medal);
        earned.addAll(medals);

        DailyChallenge challenge = DailyChallenge.forDay(day);
        int oldDaily = dailyProgress(day);
        int newDaily = Math.min(challenge.target(), oldDaily + challenge.contribution(card));
        int dailyCredits = oldDaily < challenge.target() && newDaily == challenge.target() ? challenge.credits() : 0;
        var next = new ClubhouseProgress(totalBaskets, totalUnderPar, totalAces, longest, Math.max(bestStreak, streak),
                qualifyingRounds + 1, cards, earned, day, newDaily);
        return new Result(next, streakBonus(streak), dailyCredits, List.copyOf(fresh), personalBest);
    }

    private static int streakBonus(int count) {
        if (count >= 8) return 35;
        if (count >= 5) return 20;
        return count >= 3 ? 10 : 0;
    }
}
