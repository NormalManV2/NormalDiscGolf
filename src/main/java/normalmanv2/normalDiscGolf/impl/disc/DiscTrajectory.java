package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.disc.util.MathUtil.FlightHit;
import normalmanv2.normalDiscGolf.impl.disc.util.MathUtil.HitType;
import normalmanv2.normalDiscGolf.impl.disc.GroundPhysics.Impact;
import normalmanv2.normalDiscGolf.impl.disc.GroundPhysics.Motion;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public final class DiscTrajectory {
    public record Step(Location location, boolean finished, boolean goal, boolean hazard,
                       Motion motion, Impact impact, boolean expiredInAir) {
        public Step(Location location, boolean finished, boolean goal, boolean hazard, Motion motion, Impact impact) {
            this(location, finished, goal, hazard, motion, impact, false);
        }
    }

    private final BiFunction<Location, Vector, FlightHit> collision;
    private final BiConsumer<Vector, Integer> airPhysics;
    private final Vector velocity;
    private final int maxTicks;
    private final GroundPhysics ground;
    private final ArcadeFlight arcade;
    private Location location;
    private int tick;
    private boolean finished;

    public DiscTrajectory(Location origin, Vector velocity, int maxTicks,
                   BiFunction<Location, Vector, FlightHit> collision,
                   BiConsumer<Vector, Integer> airPhysics, GroundPhysics ground) {
        this(origin, velocity, maxTicks, collision, airPhysics, ground, null);
    }

    public DiscTrajectory(Location origin, Vector velocity, int maxTicks,
                          BiFunction<Location, Vector, FlightHit> collision,
                          BiConsumer<Vector, Integer> airPhysics, GroundPhysics ground, ArcadeFlight arcade) {
        this.location = origin.clone();
        this.velocity = velocity.clone();
        this.maxTicks = maxTicks;
        this.collision = collision;
        this.airPhysics = airPhysics;
        this.ground = ground;
        this.arcade = arcade;
    }

    public Location location() { return location.clone(); }
    public ArcadeDisc arcadeEffect() { return arcade == null ? null : arcade.effect(); }

    public Step advance() {
        if (finished) throw new IllegalStateException("Flight already ended");
        boolean expired = tick++ >= maxTicks;
        if (arcade != null) arcade.beginStep(velocity, tick, !expired && ground.motion() == Motion.AIR);
        FlightHit hit = collision.apply(location, velocity);
        if (hit != null) {
            location = hit.location();
            if (arcade != null && arcade.sticks(hit)) return step(true, hit, Impact.NONE);
            Impact impact = !expired && hit.type() == HitType.BLOCK ? ground.collide(hit, velocity) : Impact.NONE;
            return step(impact == Impact.NONE, hit, impact);
        }

        location = location.clone().add(velocity);
        if (ground.motion() != Motion.AIR) {
            FlightHit support = collision.apply(location.clone().add(0, .04, 0), new Vector(0, -.36, 0));
            if (support != null) {
                location = support.location();
                boolean stopped = support.type() != HitType.BLOCK || !ground.crossSurface(support.material(), velocity);
                return step(expired || stopped, support, Impact.NONE);
            }
            ground.fall(velocity);
        } else if (!expired) {
            airPhysics.accept(velocity, tick);
        }
        return step(expired, null, Impact.NONE);
    }

    private Step step(boolean done, FlightHit hit, Impact impact) {
        finished = done;
        return new Step(location(), finished, hit != null && hit.goal(),
                hit != null && hit.hazard(), ground.motion(), impact, done && hit == null && ground.motion() == Motion.AIR);
    }

    static int maxTicks(DiscType type, boolean putting) {
        if (putting) return 30;
        return switch (type) {
            case DRIVER -> 100;
            case MID_RANGE -> 80;
            case PUTTER -> 60;
        };
    }

    public static double launchSpeed(DiscImpl disc, PlayerSkills skills, double power, boolean putting) {
        double speed = ratedLaunchSpeed(disc) * power;
        return putting ? speed * .68 * Math.min(1.12, skills.launchSpeedMultiplier()) : speed * skills.launchSpeedMultiplier();
    }

    public static double ratedLaunchSpeed(DiscImpl disc) {
        double base = switch (disc.getType()) {
            case DRIVER -> Constants.DRIVER_BASE_SPEED;
            case MID_RANGE -> Constants.MID_BASE_SPEED;
            case PUTTER -> Constants.PUTTER_BASE_SPEED;
        };
        return DiscFlightEngine.ratedLaunchSpeed(disc, base);
    }

    public static void applyReleasePhysics(Vector velocity, int tick, ShotSetup.Angle angle, boolean forehand,
                                           boolean putting, HoleWind wind) {
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        if (!putting && angle.bank() != 0 && horizontal > .00001) {
            double bank = angle.bank() * (forehand ? 1 : -1) * .035 * Math.max(0, 1 - tick / 32.0);
            velocity.add(new Vector(-velocity.getZ() / horizontal * bank, 0, velocity.getX() / horizontal * bank));
        }
        velocity.add(wind.acceleration(tick).multiply(putting ? .15 : 1));
    }
}
