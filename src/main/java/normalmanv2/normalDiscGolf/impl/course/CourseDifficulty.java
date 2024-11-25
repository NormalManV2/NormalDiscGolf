package normalmanv2.normalDiscGolf.impl.course;

public enum CourseDifficulty {
    EASY(0.2),
    MEDIUM(0.5),
    HARD(0.8);

    private final double densityFactor;

    CourseDifficulty(double densityFactor) {
        this.densityFactor = densityFactor;
    }

    public double getDensityFactor() {
        return this.densityFactor;
    }

}
