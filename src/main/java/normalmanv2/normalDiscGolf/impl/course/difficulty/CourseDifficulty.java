package normalmanv2.normalDiscGolf.impl.course.difficulty;

import static normalmanv2.normalDiscGolf.impl.util.Constants.DEFAULT_EASY_COURSE_DIFFICULTY;
import static normalmanv2.normalDiscGolf.impl.util.Constants.DEFAULT_MEDIUM_COURSE_DIFFICULTY;
import static normalmanv2.normalDiscGolf.impl.util.Constants.DEFAULT_HARD_COURSE_DIFFICULTY;

public enum CourseDifficulty {
    EASY(DEFAULT_EASY_COURSE_DIFFICULTY),
    MEDIUM(DEFAULT_MEDIUM_COURSE_DIFFICULTY),
    HARD(DEFAULT_HARD_COURSE_DIFFICULTY);

    private final double densityFactor;

    CourseDifficulty(double densityFactor) {
        this.densityFactor = densityFactor;
    }

    public double getDensityFactor() {
        return this.densityFactor;
    }

}
