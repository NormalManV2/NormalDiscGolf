package normalmanv2.normalDiscGolf.impl.player.score;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ScoreCard {

    private int totalStrokes;
    private int totalScore;
    private final Map<Integer, Integer> holeScores;
    private int currentStrokes;

    public ScoreCard() {
        this.totalStrokes = 0;
        this.totalScore = 0;
        this.currentStrokes = 0;
        this.holeScores = new HashMap<>();
    }

    public void recordHoleScore(int holeNumber, int strokes, int par) {
        this.holeScores.put(holeNumber, strokes);
        this.totalStrokes += strokes;
        int scoreRelativeToPar = strokes - par;
        this.totalScore += scoreRelativeToPar;
    }

    public void trackStroke() {
        this.currentStrokes++;
    }

    public void resetCurrentStrokes() {
        this.currentStrokes = 0;
    }

    public void addPenalty(int penaltyStrokes) {
        this.totalStrokes += penaltyStrokes;
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
