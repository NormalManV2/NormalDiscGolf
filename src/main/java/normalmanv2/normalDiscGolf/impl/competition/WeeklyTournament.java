package normalmanv2.normalDiscGolf.impl.competition;

import normalmanv2.normalDiscGolf.impl.util.Bounds;
import org.bukkit.configuration.ConfigurationSection;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record WeeklyTournament(Definition definition, Status status, Map<UUID, String> entrants, Set<UUID> starters,
                               List<StrokeStandings.Entry> results, String note, boolean prizesPaid) {
    public enum Status { SCHEDULED, RUNNING, FINISHED, CANCELED }
    public static final Duration START_GRACE = Duration.ofMinutes(5);

    public WeeklyTournament {
        entrants = Map.copyOf(entrants);
        starters = Set.copyOf(starters);
        results = List.copyOf(results);
    }

    public record Definition(Instant start, Instant opens, int holes, int minimum, int capacity, List<Integer> prizes) {
        public Definition {
            prizes = List.copyOf(prizes);
            if (opens.isAfter(start) || holes < 1 || holes > 18 || minimum < 2 || capacity < minimum || capacity > 8
                    || prizes.size() > capacity || prizes.stream().anyMatch(credits -> credits < 0))
                throw new IllegalArgumentException("Invalid weekly tournament settings");
        }
        public String id() { return "weekly-" + start.getEpochSecond(); }
        public LocalDate date() { return start.atZone(ZoneOffset.UTC).toLocalDate(); }
        public int prize(int place) { return place > 0 && place <= prizes.size() ? prizes.get(place - 1) : 0; }
    }

    public static Definition next(ConfigurationSection config, Instant notBefore) {
        String prefix = "weekly-tournament.";
        var day = DayOfWeek.valueOf(config.getString(prefix + "day", "saturday").trim().toUpperCase(Locale.ROOT));
        var time = LocalTime.parse(config.getString(prefix + "time-utc", "20:00")).withNano(0);
        var date = notBefore.atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.nextOrSame(day));
        Instant start = date.atTime(time).toInstant(ZoneOffset.UTC);
        if (start.isBefore(notBefore)) start = date.plusWeeks(1).atTime(time).toInstant(ZoneOffset.UTC);
        int capacity = Bounds.clamp(config.getInt(prefix + "max-players", 8), 2, 8);
        List<Integer> prizes = config.isList(prefix + "prizes") ? config.getIntegerList(prefix + "prizes") : List.of(100, 60, 30);
        return new Definition(start, start.minus(Duration.ofHours(Bounds.clamp(config.getInt(prefix + "signup-hours", 72), 1, 168))),
                Bounds.clamp(config.getInt(prefix + "holes", 9), 1, 18),
                Bounds.clamp(config.getInt(prefix + "min-players", 2), 2, capacity), capacity,
                prizes.stream().limit(capacity).toList());
    }

    public static WeeklyTournament scheduled(Definition definition) {
        return new WeeklyTournament(definition, Status.SCHEDULED, Map.of(), Set.of(), List.of(), "", false);
    }

    public boolean registrationOpen(Instant now) {
        return status == Status.SCHEDULED && !now.isBefore(definition.opens()) && now.isBefore(definition.start());
    }

    public WeeklyTournament withEntrant(UUID player, String name) {
        var updated = new HashMap<>(entrants);
        if (name == null) updated.remove(player);
        else updated.put(player, name);
        return new WeeklyTournament(definition, status, updated, starters, results, note, prizesPaid);
    }

    public WeeklyTournament start(Set<UUID> admitted) {
        return new WeeklyTournament(definition, Status.RUNNING, entrants, admitted, List.of(), "", false);
    }

    public WeeklyTournament finish(List<StrokeStandings.Entry> scores) {
        return new WeeklyTournament(definition, Status.FINISHED, entrants, starters, scores, "", false);
    }

    public WeeklyTournament cancel(String reason) {
        return new WeeklyTournament(definition, Status.CANCELED, entrants, starters, List.of(), reason, true);
    }

    public WeeklyTournament markPrizesPaid() {
        return new WeeklyTournament(definition, status, entrants, starters, results, note, true);
    }
}
