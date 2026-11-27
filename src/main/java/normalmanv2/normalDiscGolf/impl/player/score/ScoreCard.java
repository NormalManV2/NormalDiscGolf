package normalmanv2.normalDiscGolf.impl.player.score;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public class ScoreCard {

    private int totalStrokes;
    private int totalScore;
    private final Map<Integer, Integer> holeScores;
    private int currentStrokes;
    private final Map<Integer, Basket> baskets = new HashMap<>();
    private final Set<UUID> participants = new HashSet<>();
    private int basketStreak;
    private int bestBasketStreak;

    public record Basket(int strokes, int par, UUID player, double distance) {
        public boolean underPar() { return strokes < par; }
        public boolean ace() { return strokes == 1; }
    }

    public ScoreCard() {
        this.totalStrokes = 0;
        this.totalScore = 0;
        this.currentStrokes = 0;
        this.holeScores = new HashMap<>();
    }

    public void recordHoleScore(int holeNumber, int strokes, int par) {
        recordScore(holeNumber, strokes, par);
        breakBasketStreak();
    }

    public void breakBasketStreak() { basketStreak = 0; }

    private void recordScore(int holeNumber, int strokes, int par) {
        if (holeNumber < 0 || strokes < 1 || par < 1) throw new IllegalArgumentException("Invalid hole score");
        if (this.holeScores.containsKey(holeNumber)) throw new IllegalStateException("Hole already recorded");
        this.holeScores.put(holeNumber, strokes);
        this.totalStrokes += strokes;
        int scoreRelativeToPar = strokes - par;
        this.totalScore += scoreRelativeToPar;
    }

    public void undoStroke() { this.currentStrokes = Math.max(0, this.currentStrokes - 1); }

    public void recordBasket(int holeNumber, int strokes, int par, UUID player, double distance) {
        recordScore(holeNumber, strokes, par);
        baskets.put(holeNumber, new Basket(strokes, par, player, distance));
        basketStreak++;
        bestBasketStreak = Math.max(bestBasketStreak, basketStreak);
    }

    public int getHoledOutCount() { return baskets.size(); }
    public Map<Integer, Basket> getBaskets() { return Collections.unmodifiableMap(baskets); }
    public int getBasketStreak() { return basketStreak; }
    public int getBestBasketStreak() { return bestBasketStreak; }
    public void recordParticipation(UUID player) { participants.add(player); }
    public boolean participated(UUID player) { return participants.contains(player); }

    public void trackStroke() {
        this.currentStrokes++;
    }

    public void resetCurrentStrokes() {
        this.currentStrokes = 0;
    }

    public void addPenalty(int penaltyStrokes) {
        if (penaltyStrokes < 0) throw new IllegalArgumentException("Penalty must be nonnegative");
        this.currentStrokes += penaltyStrokes;
    }

    public int getTotalScore() {
        return this.totalScore;
    }

    public int getTotalStrokes() {
        return this.totalStrokes;
    }

    public int getCurrentStrokes() {
        return this.currentStrokes;
    }

    public Map<Integer, Integer> getHoleScores() {
        return Collections.unmodifiableMap(this.holeScores);
    }

}
