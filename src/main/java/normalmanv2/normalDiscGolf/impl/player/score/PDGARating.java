package normalmanv2.normalDiscGolf.impl.player.score;

import normalmanv2.normalDiscGolf.impl.util.Bounds;
import normalmanv2.normalDiscGolf.common.division.Division;

public class PDGARating {
    private static final int STARTING_RATING = 775;

    private int totalRounds;
    private double totalScore;
    private double averageScore;
    private int rating;
    private int peakRating;
    private int lastRoundRating;
    private Division division;

    public PDGARating() {
        this.totalRounds = 0;
        this.totalScore = 0;
        this.averageScore = 0;
        this.rating = 0;
        this.division = Division.JUNIOR;
    }

    public record Change(int before, int after, int roundRating, CareerDivision previousDivision, CareerDivision division) {}

    public Change recordRound(int scoreToPar, int holes, int difficulty) {
        if (holes < 1 || holes > 18) throw new IllegalArgumentException("Rated rounds need 1–18 holes.");
        int before = rating;
        var previousDivision = getCareerDivision();
        double normalizedScore = scoreToPar * 18.0 / holes;
        int courseDifficulty = difficulty == 0 ? 50 : Bounds.clamp(difficulty, 1, 100);
        lastRoundRating = (int)Math.round(Bounds.clamp(900 + (courseDifficulty - 50) * 1.5 - normalizedScore * 6, 600, 1200));
        double weight = Math.min(.60, holes / (holes + 6.0));
        int baseline = before == 0 ? STARTING_RATING : before;
        setRating((int)Math.round(baseline + (lastRoundRating - baseline) * weight));
        totalScore += normalizedScore;
        totalRounds++;
        averageScore = totalScore / totalRounds;
        return new Change(before, rating, lastRoundRating, previousDivision, getCareerDivision());
    }

    public void setDivision(Division division) {
        this.division = division;
    }

    public void setRating(int rating) {
        if (rating < 0) {
            throw new IllegalArgumentException("Rating must be nonnegative.");
        }
        this.rating = rating;
        this.peakRating = Math.max(peakRating, rating);
    }

    public void restore(int totalRounds, double totalScore, double averageScore, int rating, Division division) {
        restore(totalRounds, totalScore, averageScore, rating, division, rating, 0);
    }

    public void restore(int totalRounds, double totalScore, double averageScore, int rating, Division division,
                        int peakRating, int lastRoundRating) {
        this.totalRounds = Math.max(0, totalRounds);
        this.totalScore = Double.isFinite(totalScore) ? totalScore : 0;
        this.averageScore = this.totalRounds == 0 || !Double.isFinite(averageScore) ? 0 : averageScore;
        this.rating = Math.max(0, rating);
        this.peakRating = Math.max(this.rating, peakRating);
        this.lastRoundRating = Bounds.clamp(lastRoundRating, 0, 1200);
        this.division = division == null ? Division.JUNIOR : division;
    }

    public CareerDivision getCareerDivision() { return CareerDivision.forRating(rating); }
    public int getPeakRating() { return peakRating; }
    public int getLastRoundRating() { return lastRoundRating; }

    public double getAverageScore() {
        return this.averageScore;
    }

    public double getTotalScore() {
        return this.totalScore;
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
