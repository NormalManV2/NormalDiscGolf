package normalmanv2.normalDiscGolf.impl.round;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MatchPlayScore {
    public record Hole(int index, UUID winner, int firstStrokes, int secondStrokes, String awardReason) {}

    private final UUID first;
    private final UUID second;
    private final int totalHoles;
    private final List<Hole> holes = new ArrayList<>();
    private UUID forfeiter;

    public MatchPlayScore(UUID first, UUID second, int totalHoles) {
        if (first.equals(second) || totalHoles < 1) throw new IllegalArgumentException("Invalid match");
        this.first = first;
        this.second = second;
        this.totalHoles = totalHoles;
    }

    public UUID first() { return first; }
    public UUID second() { return second; }
    public UUID forfeiter() { return forfeiter; }
    public List<Hole> holes() { return List.copyOf(holes); }
    public int holesRemaining() { return totalHoles - holes.size(); }
    public int wins(UUID player) { return (int) holes.stream().filter(hole -> player.equals(hole.winner())).count(); }
    public int halved() { return holes.size() - wins(first) - wins(second); }
    public int margin() { return Math.abs(wins(first) - wins(second)); }

    public UUID leader() {
        int comparison = Integer.compare(wins(first), wins(second));
        return comparison > 0 ? first : comparison < 0 ? second : null;
    }

    public UUID winner() {
        if (!isComplete()) return null;
        return forfeiter == null ? leader() : opponent(forfeiter);
    }
    public boolean isComplete() { return forfeiter != null || holesRemaining() == 0 || margin() > holesRemaining(); }

    private UUID opponent(UUID player) {
        if (first.equals(player)) return second;
        if (second.equals(player)) return first;
        throw new IllegalArgumentException("Player is outside this match");
    }

    public Hole recordStrokes(int index, int firstStrokes, int secondStrokes) {
        if (firstStrokes < 1 || secondStrokes < 1) throw new IllegalArgumentException("Invalid strokes");
        UUID winner = firstStrokes < secondStrokes ? first : firstStrokes > secondStrokes ? second : null;
        return record(new Hole(index, winner, firstStrokes, secondStrokes, null));
    }

    public Hole award(int index, UUID conceding, String reason) {
        return record(new Hole(index, opponent(conceding), 0, 0, reason));
    }

    private Hole record(Hole result) {
        if (isComplete() || result.index() != holes.size()) throw new IllegalStateException("Unexpected match hole");
        holes.add(result);
        return result;
    }

    public void forfeit(UUID player) {
        opponent(player);
        if (!isComplete()) forfeiter = player;
    }
}
