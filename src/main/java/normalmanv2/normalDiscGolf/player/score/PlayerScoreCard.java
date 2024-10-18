package normalmanv2.normalDiscGolf.player.score;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PlayerScoreCard {

    private int totalStrokes;
    private int totalScore;
    private final Map<Integer, Integer> holeScores;

    public PlayerScoreCard() {
        this.totalStrokes = 0;
        this.totalScore = 0;
        this.holeScores = new HashMap<>();
    }

    public void recordHoleScore(int holeNumber, int strokes, int par) {
        this.holeScores.put(holeNumber, strokes);
        this.totalStrokes += strokes;
        int scoreRelativeToPar = strokes - par;
        this.totalScore += scoreRelativeToPar;
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

    public Map<Integer, Integer> getHoleScores() {
        return Collections.unmodifiableMap(this.holeScores);
    }

}
