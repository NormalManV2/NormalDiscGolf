package normalmanv2.normalDiscGolf.impl.util;

import normalmanv2.normalDiscGolf.common.division.Division;

public class Constants {
    public static final double STRAIGHT_PHASE_END = 0.2;
    public static final double TURN_PHASE_END = 0.4;
    public static final double FADE_PHASE_START = 0.4;
    public static final double TURN_ADJUSTMENT = 0.15;
    public static final double FADE_ADJUSTMENT = 0.15;
    public static final double DRAG_FACTOR = 0.98;
    public static final double DRIVER_BASE_SPEED = 2.5;
    public static final double MID_BASE_SPEED = 1.9;
    public static final double PUTTER_BASE_SPEED = 1.3;
    public static final double POWER_ADJUSTMENT = 0.05;
    public static final double FORM_ADJUSTMENT = 0.02;
    public static final double ACCURACY_ADJUSTMENT = 0.5;
    public static final int FFA_ROUND_MAX_PLAYERS = 8;
    public static final int DOUBLES_ROUND_MAX_TEAMS = 4;
    public static final int MAXIMUM_PARTY_MEMBERS = 4;
    public static final int DEFAULT_FFA_COURSE_SIZE = 16;
    public static final Division DEFAULT_FFA_COURSE_DIVISION = Division.ADVANCED;
    public static final int DEFAULT_DUBS_COURSE_SIZE = 18;
    public static final Division DEFAULT_DUBS_COURSE_DIVISION = Division.MASTER;
    public static final int DEFAULT_COURSE_HOLES = 18;

    public static final double DEFAULT_EASY_COURSE_DIFFICULTY = 0.2;
    public static final double DEFAULT_MEDIUM_COURSE_DIFFICULTY = 0.5;
    public static final double DEFAULT_HARD_COURSE_DIFFICULTY = 0.8;

    public static final int DEFAULT_TILE_SIZE = 16;
    public static final int DEFAULT_TILE_Y = 64;

    public static final int DEFAULT_TILE_OBSTACLES_LIMIT = 14;
    public static final int DEFAULT_FAIRWAY_TILE_OBSTACLES_LIMIT = 18;
    public static final int DEFAULT_OBSTACLE_TILE_OBSTACLES_LIMIT = 25;
}
