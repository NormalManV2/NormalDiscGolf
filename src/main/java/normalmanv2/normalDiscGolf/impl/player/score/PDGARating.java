package normalmanv2.normalDiscGolf.impl.player.score;

import normalmanv2.normalDiscGolf.impl.player.Division;
import normalmanv2.normalDiscGolf.impl.round.CourseDifficulty;

public class PDGARating {

    private int totalRounds;
    private double totalScore;
    private double averageScore;
    private int rating;
    private Division division;

    public PDGARating() {
        this.totalRounds = 0;
        this.totalScore = 0;
        this.averageScore = 0;
        this.rating = 0;
        this.division = Division.JUNIOR;
    }

    public void handleRoundEnd(double roundScore) {
        this.totalScore += roundScore;
        this.totalRounds++;
        this.averageScore = this.totalScore / this.totalRounds;
    }

    private void updateRating(CourseDifficulty difficulty) {
        this.rating = calculateRating(this.averageScore, this.division, difficulty);
    }

    private int calculateRating(double averageScore, Division division, CourseDifficulty difficulty) {

        return (int) ((1000 - averageScore) * (1.1));
    }

    public void setDivision(Division division) {
        this.division = division;
    }

    public double getAverageScore() {
        return this.averageScore;
    }

    public int getRating() {
        return this.rating;
    }

    public int getTotalRounds() {
        return this.totalRounds;
    }

    public Division getDivision() {
        return this.division;
    }
}
