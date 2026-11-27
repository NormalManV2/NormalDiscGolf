package normalmanv2.normalDiscGolf.impl.competition;

import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RankedSeason(int number, Instant starts, Instant ends, int minimumRounds, List<Integer> prizes,
                           Map<UUID, Entry> entries, boolean prizesPaid) {
    private static final List<Integer> DEFAULT_PRIZES = List.of(300, 180, 100);

    public RankedSeason {
        if (number < 1 || !ends.isAfter(starts) || minimumRounds < 1 || prizes.stream().anyMatch(prize -> prize < 0))
            throw new IllegalArgumentException("Invalid ranked season settings");
        prizes = List.copyOf(prizes);
        entries = Map.copyOf(entries);
    }

    public record Score(UUID player, String name, int roundRating, int holes) {
        public Score {
            if (roundRating < 600 || roundRating > 1200 || holes < 1 || holes > 18)
                throw new IllegalArgumentException("Invalid rated scorecard");
        }
    }

    public record Entry(String name, int rounds, long holes, long ratingPoints) {
        public Entry {
            if (rounds < 1 || holes < rounds || holes > rounds * 18L
                    || ratingPoints < holes * 600L || ratingPoints > holes * 1200L)
                throw new IllegalArgumentException("Invalid ranked season entry");
        }

        public int rating() { return (int) Math.round((double) ratingPoints / holes); }
    }

    public record Standing(UUID player, Entry entry, int place) {}

    public static RankedSeason open(int number, Instant starts, ConfigurationSection config) {
        String prefix = "ranked-seasons.";
        return new RankedSeason(number, starts, starts.plus(length(config)),
                Math.max(1, config.getInt(prefix + "minimum-rounds", 3)),
                config.isList(prefix + "prizes") ? config.getIntegerList(prefix + "prizes") : DEFAULT_PRIZES,
                Map.of(), false);
    }

    public RankedSeason followAt(Instant date, ConfigurationSection config) {
        var duration = length(config);
        long skipped = Duration.between(ends, date).dividedBy(duration);
        return open(Math.toIntExact(number + skipped + 1), ends.plus(duration.multipliedBy(skipped)), config);
    }

    private static Duration length(ConfigurationSection config) {
        return Duration.ofDays(Math.clamp(config.getInt("ranked-seasons.length-days", 30), 1, 365));
    }

    public boolean includes(Instant date) { return !date.isBefore(starts) && date.isBefore(ends); }

    public int prize(int place) { return place > 0 && place <= prizes.size() ? prizes.get(place - 1) : 0; }

    public RankedSeason record(List<Score> scores) {
        var updated = new HashMap<>(entries);
        for (Score score : scores) {
            long points = (long) score.roundRating() * score.holes();
            Entry previous = updated.get(score.player());
            updated.put(score.player(), previous == null ? new Entry(score.name(), 1, score.holes(), points)
                    : new Entry(score.name(), previous.rounds() + 1, previous.holes() + score.holes(),
                            previous.ratingPoints() + points));
        }
        return new RankedSeason(number, starts, ends, minimumRounds, prizes, updated, prizesPaid);
    }

    public List<Standing> standings() {
        var qualified = entries.entrySet().stream().filter(entry -> entry.getValue().rounds() >= minimumRounds)
                .sorted(Comparator.<Map.Entry<UUID, Entry>>comparingInt(entry -> entry.getValue().rating()).reversed()
                        .thenComparing(entry -> entry.getValue().name(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Map.Entry::getKey)).toList();
        var rows = new ArrayList<Standing>();
        int previousRating = -1;
        int place = 0;
        for (var entry : qualified) {
            int rating = entry.getValue().rating();
            if (rating != previousRating) place = rows.size() + 1;
            rows.add(new Standing(entry.getKey(), entry.getValue(), place));
            previousRating = rating;
        }
        return List.copyOf(rows);
    }

    public RankedSeason markPrizesPaid() {
        return new RankedSeason(number, starts, ends, minimumRounds, prizes, entries, true);
    }
}
