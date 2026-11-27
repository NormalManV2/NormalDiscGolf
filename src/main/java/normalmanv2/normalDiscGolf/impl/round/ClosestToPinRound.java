package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import org.bukkit.ChatColor;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

public final class ClosestToPinRound extends SingleThrowRound {
    private final Map<Team, Map<Integer, OptionalInt>> attempts = new HashMap<>();

    public record Total(int faults, long tenths) implements Comparable<Total> {
        public static Total from(Collection<OptionalInt> attempts) {
            int faults = 0;
            long tenths = 0;
            for (var distance : attempts) {
                if (distance.isPresent()) tenths += distance.getAsInt();
                else faults++;
            }
            return new Total(faults, tenths);
        }

        @Override
        public int compareTo(Total other) {
            int byFaults = Integer.compare(faults, other.faults);
            return byFaults == 0 ? Long.compare(tenths, other.tenths) : byFaults;
        }

        public String label() {
            return distanceLabel(tenths) + (faults == 0 ? "" : " (" + faults + (faults == 1 ? " fault)" : " faults)"));
        }
    }

    public record Standing(int place, String name, Total total) {
        public String line() { return place + ". " + name + " - " + total.label(); }
    }

    public ClosestToPinRound(CourseImpl course, String id, DefaultRoundSettings settings) {
        super(course, id, settings);
    }

    @Override
    public String holeResult(Team team) {
        var distance = attempts.getOrDefault(team, Map.of()).get(getCurrentHole());
        if (distance == null) return "Pending";
        return distance.isEmpty() ? "No score" : distanceLabel(distance.getAsInt());
    }

    public Total total(Team team) { return Total.from(attempts.getOrDefault(team, Map.of()).values()); }

    @Override
    public void finishThrow(Team team, Location landing, boolean hazard) {
        if (hazard) {
            super.finishThrow(team, landing, true);
            return;
        }
        if (hasFinishedHole(team)) return;
        Location basket = getCourse().basket(landing.getWorld(), getCurrentHole());
        int distance = distanceTenths(landing, basket);
        completeWithoutBasket(team);
        recordDistance(team, distance);
    }

    @Override
    public void miss(Team team, String reason) {
        if (hasFinishedHole(team)) return;
        completeWithoutBasket(team);
        record(team, OptionalInt.empty());
        play().message(ChatColor.GRAY + name(team.getOwner()) + " got no score on hole " + (getCurrentHole() + 1)
                + " (" + reason + ").");
    }

    public void recordDistance(Team team, int tenths) {
        record(team, OptionalInt.of(tenths));
        play().message(ChatColor.GOLD + name(team.getOwner()) + " finished hole " + (getCurrentHole() + 1)
                + " at " + distanceLabel(tenths) + ".");
    }

    private void record(Team team, OptionalInt distance) {
        attempts.computeIfAbsent(team, ignored -> new HashMap<>()).put(getCurrentHole(), distance);
    }

    public List<Standing> standings() {
        var entries = getTeams().stream()
                .map(team -> new Standing(0, name(team.getOwner()), total(team)))
                .sorted(Comparator.comparing(Standing::total).thenComparing(Standing::name)).toList();
        var standings = new ArrayList<Standing>(entries.size());
        Total previous = null;
        int place = 0;
        for (var entry : entries) {
            if (!entry.total().equals(previous)) place = standings.size() + 1;
            standings.add(new Standing(place, entry.name(), entry.total()));
            previous = entry.total();
        }
        return standings;
    }

    public String result(List<Standing> standings) {
        var leaders = standings.stream().filter(entry -> entry.place() == 1).map(Standing::name).toList();
        String score = standings.getFirst().total().label();
        return leaders.size() == 1 ? leaders.getFirst() + " wins Closest to Pin with " + score + "."
                : String.join(", ", leaders) + " tie for first in Closest to Pin with " + score + " each.";
    }

    public static int distanceTenths(Location landing, Location basket) {
        return (int) Math.round(10 * Math.hypot(landing.getX() - basket.getX(), landing.getZ() - basket.getZ()));
    }

    private static String distanceLabel(long tenths) {
        return String.format(Locale.ROOT, "%.1f blocks", tenths / 10.0);
    }
}
