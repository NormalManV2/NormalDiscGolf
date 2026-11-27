package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.disc.ArcadeDisc;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ArcadeRound extends FFARound {
    private final int chargeLimit;
    private final Map<UUID, EnumMap<ArcadeDisc, Integer>> used = new HashMap<>();

    public ArcadeRound(CourseImpl course, String id, DefaultRoundSettings settings, int chargeLimit) {
        super(course, id, settings);
        this.chargeLimit = Math.clamp(chargeLimit, 0, 9);
    }

    public int chargeLimit() { return chargeLimit; }

    public int remaining(UUID player, ArcadeDisc disc) {
        var charges = used.get(player);
        return chargeLimit - (charges == null ? 0 : charges.getOrDefault(disc, 0));
    }

    public boolean spend(UUID player, ArcadeDisc disc) {
        if (isOver() || getState() != RoundState.START || !containsPlayer(player) || remaining(player, disc) <= 0) return false;
        used.computeIfAbsent(player, ignored -> new EnumMap<>(ArcadeDisc.class)).merge(disc, 1, Integer::sum);
        return true;
    }

    public void refund(UUID player, ArcadeDisc disc) {
        var charges = used.get(player);
        if (charges != null) charges.computeIfPresent(disc, (ignored, amount) -> amount > 1 ? amount - 1 : null);
    }

    public String chargeSummary(UUID player) {
        return "Arcade charges • Phantom " + remaining(player, ArcadeDisc.PHANTOM)
                + " • Rocket " + remaining(player, ArcadeDisc.ROCKET) + " • Sticky " + remaining(player, ArcadeDisc.STICKY);
    }
}
