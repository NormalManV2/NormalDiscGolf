package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.api.disc.DiscType;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** All players share a deterministic rack that rotates on UTC time, including across restarts. */
public record DiscStock(Set<DiscModel> offers, Instant restockAt) {
    public static DiscStock at(Instant now, int rotationHours, List<DiscModel> catalog) {
        if (rotationHours <= 0) return new DiscStock(Set.copyOf(catalog), null);
        long period = Math.min(rotationHours, 168) * 3600L;
        long cycle = Math.floorDiv(now.getEpochSecond(), period);
        var offers = new LinkedHashSet<DiscModel>();
        for (DiscType type : DiscType.values()) {
            var choices = catalog.stream().filter(d -> d.type() == type && d.price() > 0).toList();
            if (choices.isEmpty()) continue;
            offers.add(choices.get(Math.floorMod(cycle, choices.size())));
        }
        return new DiscStock(Set.copyOf(offers), Instant.ofEpochSecond((cycle + 1) * period));
    }
}
