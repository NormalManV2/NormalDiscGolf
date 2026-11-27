package normalmanv2.normalDiscGolf.impl.competition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class StrokeStandings {
    private StrokeStandings() {}

    public record Entry(UUID player, String name, int strokes, int relativeToPar) {
        public String score() {
            String relative = relativeToPar > 0 ? "+" + relativeToPar : Integer.toString(relativeToPar);
            return relative + " (" + strokes + (strokes == 1 ? " stroke)" : " strokes)");
        }
    }

    public record Standing(int place, Entry entry) {
        public String line() { return place + ". " + entry.name() + " - " + entry.score(); }
    }

    public static List<Standing> rank(Collection<Entry> entries) {
        var ordered = entries.stream().sorted(Comparator.comparingInt(Entry::strokes)
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::player)).toList();
        var rows = new ArrayList<Standing>(ordered.size());
        int place = 0, previous = -1;
        for (Entry entry : ordered) {
            if (entry.strokes() != previous) place = rows.size() + 1;
            rows.add(new Standing(place, entry));
            previous = entry.strokes();
        }
        return List.copyOf(rows);
    }
}
