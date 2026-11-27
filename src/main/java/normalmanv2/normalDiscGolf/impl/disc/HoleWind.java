package normalmanv2.normalDiscGolf.impl.disc;

import org.bukkit.util.Vector;

import java.util.SplittableRandom;
import java.util.Locale;

public record HoleWind(double heading, double strength, double gustPhase, double gustAmplitude, double swayDegrees) {
    public static final HoleWind CALM = new HoleWind(0, 0, 0);

    public HoleWind(double heading, double strength, double gustPhase) {
        this(heading, strength, gustPhase, .22, 0);
    }

    public static HoleWind forHole(long seed, int hole) {
        var random = new SplittableRandom(seed + 0x9E3779B97F4A7C15L * (hole + 1L));
        return new HoleWind(random.nextDouble(360), random.nextDouble(.25, 2.75), random.nextDouble(Math.PI * 2));
    }

    public static HoleWind thunderstorm(long seed, int hole, double scale) {
        var random = new SplittableRandom(seed ^ (0x632BE59BD9B4E019L * (hole + 1L)));
        return new HoleWind(random.nextDouble(360), random.nextDouble(4, 5.5) * scale,
                random.nextDouble(Math.PI * 2), .55, 35);
    }

    Vector acceleration(int tick) {
        double angle = Math.toRadians(heading + swayDegrees * Math.sin(gustPhase + tick * .09));
        double force = strength * .002 * (1 + gustAmplitude * Math.sin(gustPhase + tick * .17));
        return new Vector(-Math.sin(angle) * force, 0, Math.cos(angle) * force);
    }

    public String description() {
        return description(1);
    }

    public String description(int readingLevel) {
        if (strength == 0) return "Wind: calm";
        String[] directions = readingLevel >= 2
                ? new String[]{"S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW", "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE"}
                : new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        String direction = directions[(int) Math.round(heading * directions.length / 360) % directions.length];
        String detail = readingLevel >= 4 ? String.format(Locale.ROOT, " (strength %.1f)", strength) : "";
        if (swayDegrees > 0) return "Wind: shifting storm gusts, generally toward " + direction + detail;
        return "Wind: " + (strength < 1 ? "light" : strength < 2 ? "steady" : "gusty") + " toward " + direction + detail;
    }
}
