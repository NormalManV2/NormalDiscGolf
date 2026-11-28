package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.team.Team;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** The attempts for one team stroke, held until the team commits a lie. */
final class PairedShotTurn {
    enum Outcome { BASKET, LEGAL, HAZARD }

    record Attempt(UUID player, String name, Location lie, Outcome outcome, double basketDistance, RoundSpectating.Clip clip) {
        Attempt(UUID player, String name, Location lie, Outcome outcome, double basketDistance) {
            this(player, name, lie, outcome, basketDistance, null);
        }
        Attempt { lie = lie.clone(); }
        @Override public Location lie() { return lie.clone(); }
    }

    private final Team team;
    private final List<Attempt> attempts = new ArrayList<>(2);
    private UUID choiceId;

    PairedShotTurn(Team team) { this.team = team; }
    Team team() { return team; }
    UUID choiceId() { return choiceId; }
    boolean choosing() { return choiceId != null; }

    UUID nextThrower() {
        return team.getTeamMembers().stream()
                .filter(id -> attempts.stream().noneMatch(attempt -> attempt.player().equals(id)))
                .findFirst().orElse(null);
    }

    void record(Attempt attempt) {
        if (choosing() || !attempt.player().equals(nextThrower()))
            throw new IllegalStateException("Unexpected paired attempt");
        attempts.add(attempt);
    }

    List<Attempt> legalLies() { return attempts.stream().filter(attempt -> attempt.outcome() == Outcome.LEGAL).toList(); }

    void openChoice() { choiceId = UUID.randomUUID(); }

    Attempt fallback(Location basket) {
        return legalLies().stream().min(Comparator.comparingDouble(attempt -> attempt.lie.distanceSquared(basket)))
                .orElse(attempts.get(0));
    }

    Attempt worst(Location basket) {
        for (Attempt attempt : attempts) if (attempt.outcome() == Outcome.HAZARD) return attempt;
        return legalLies().stream().max(Comparator.comparingDouble(attempt -> attempt.lie.distanceSquared(basket)))
                .orElseGet(() -> attempts.get(attempts.size() - 1));
    }
}
