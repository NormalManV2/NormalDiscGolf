package normalmanv2.normalDiscGolf.impl.player.score;

import java.util.HashSet;
import java.util.OptionalDouble;
import java.util.Set;

/** Individual throw statistics, independent of the team's scorecard. */
public final class RoundAnalytics {
    public enum Finish { BASKET, FAIRWAY, OTHER, FAULT }

    private final Set<Integer> playedTees = new HashSet<>();
    private int drives;
    private int fairwayHits;
    private int puttAttempts;
    private int puttsMade;
    private int obPenalties;
    private double longestDrive = -1;
    private double longestPutt = -1;

    public void recordThrow(int hole, boolean atTee, boolean putting, Finish finish, double distance) {
        boolean firstTee = atTee && playedTees.add(hole);
        if (putting) {
            puttAttempts++;
            if (finish == Finish.BASKET) {
                puttsMade++;
                longestPutt = Math.max(longestPutt, distance);
            }
        } else if (firstTee) {
            drives++;
            if (finish == Finish.BASKET || finish == Finish.FAIRWAY) fairwayHits++;
            if (finish != Finish.FAULT) longestDrive = Math.max(longestDrive, distance);
        }
    }

    public void recordPenalty() { obPenalties++; }

    public Stats snapshot() {
        return new Stats(distance(longestDrive), distance(longestPutt), drives, fairwayHits, puttAttempts, puttsMade, obPenalties);
    }

    private static OptionalDouble distance(double value) {
        return value < 0 ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    public record Stats(OptionalDouble longestDrive, OptionalDouble longestPutt, int drives, int fairwayHits,
                        int puttAttempts, int puttsMade, int obPenalties) {
        public static final Stats EMPTY = new Stats(OptionalDouble.empty(), OptionalDouble.empty(), 0, 0, 0, 0, 0);
    }
}
