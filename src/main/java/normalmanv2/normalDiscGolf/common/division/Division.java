package normalmanv2.normalDiscGolf.common.division;

import normalmanv2.normalDiscGolf.impl.course.difficulty.CourseDifficulty;

public enum Division {

    JUNIOR(200, CourseDifficulty.EASY),
    RECREATIONAL(300, CourseDifficulty.EASY),
    INTERMEDIATE(400, CourseDifficulty.EASY),
    ADVANCED(500, CourseDifficulty.MEDIUM),
    PROFESSIONAL(600, CourseDifficulty.MEDIUM),
    SENIOR(700, CourseDifficulty.MEDIUM),
    MASTER(800, CourseDifficulty.HARD),
    GRANDMASTER(850, CourseDifficulty.HARD),
    LEGEND(900, CourseDifficulty.HARD);

    private final int ratingThreshold;
    private final CourseDifficulty difficulty;

    Division(int ratingThreshold, CourseDifficulty difficulty) {
        this.ratingThreshold = ratingThreshold;
        this.difficulty = difficulty;
    }

    public int getRatingThreshold() {
        return ratingThreshold;
    }
    public CourseDifficulty getDifficulty() {
        return difficulty;
    }

}
