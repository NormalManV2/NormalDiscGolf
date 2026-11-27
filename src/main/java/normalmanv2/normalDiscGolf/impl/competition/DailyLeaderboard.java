package normalmanv2.normalDiscGolf.impl.competition;

import normalmanv2.normalDiscGolf.impl.competition.StrokeStandings.Entry;
import normalmanv2.normalDiscGolf.impl.competition.StrokeStandings.Standing;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DailyLeaderboard(DailyCourse.Definition course, Map<UUID, Entry> entries) {
    public DailyLeaderboard { entries = Map.copyOf(entries); }

    public DailyLeaderboard withResults(Collection<Entry> results) {
        Map<UUID, Entry> updated = new HashMap<>(entries);
        for (Entry result : results)
            updated.merge(result.player(), result, (best, attempt) -> attempt.strokes() < best.strokes() ? attempt : best);
        return entries.equals(updated) ? this : new DailyLeaderboard(course, updated);
    }

    public List<Standing> standings() {
        return StrokeStandings.rank(entries.values());
    }
}
