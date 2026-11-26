package normalmanv2.normalDiscGolf.impl.player.score;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ScoreCard {

    private int totalStrokes;
    private int totalScore;
    private final Map<Integer, Integer> holeScores;
    private int currentStrokes;
    private int holedOutCount;

    public ScoreCard() {
        this.totalStrokes = 0;
        this.totalScore = 0;
        this.currentStrokes = 0;
        this.holeScores = new HashMap<>();
    }

    public void recordHoleScore(int holeNumber, int strokes, int par) {
        if (holeNumber < 0 || strokes < 1 || par < 1) throw new IllegalArgumentException("Invalid hole score");
        if (this.holeScores.containsKey(holeNumber)) throw new IllegalStateException("Hole already recorded");
        this.holeScores.put(holeNumber, strokes);
        this.totalStrokes += strokes;
        int scoreRelativeToPar = strokes - par;
        this.totalScore += scoreRelativeToPar;
    }

    public void undoStroke() { this.currentStrokes = Math.max(0, this.currentStrokes - 1); }

    public void recordBasket(int holeNumber, int strokes, int par) {
        recordHoleScore(holeNumber, strokes, par);
        holedOutCount++;
    }

    public int getHoledOutCount() { return holedOutCount; }

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
