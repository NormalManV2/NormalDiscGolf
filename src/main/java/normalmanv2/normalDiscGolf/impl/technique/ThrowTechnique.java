package normalmanv2.normalDiscGolf.impl.technique;

import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.disc.DiscTrajectory;
import normalmanv2.normalDiscGolf.impl.technique.data.TechniquePhysicsData;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public abstract class ThrowTechnique {
    private final String id;

    public ThrowTechnique(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public void applyPhysics(DiscImpl discImpl, Vector discVelocity, int tick, int maxTicks, BlockFace direction) {
        applyPhysics(discImpl, discVelocity, tick, maxTicks, direction, Constants.DRAG_FACTOR, 1);
    }

    public void applyPhysics(DiscImpl discImpl, Vector discVelocity, int tick, int maxTicks, BlockFace direction,
                             double drag, double fadeMultiplier) {
        applyPhysics(discImpl, discVelocity, tick, maxTicks, direction, drag, 1, fadeMultiplier);
    }

    public void applyPhysics(DiscImpl discImpl, Vector discVelocity, int tick, int maxTicks, BlockFace direction,
                             double drag, double turnMultiplier, double fadeMultiplier) {
        discVelocity.multiply(drag);
        double flightPhase = (double) tick / maxTicks;
        double speedRatio = Math.hypot(discVelocity.getX(), discVelocity.getZ()) / DiscTrajectory.ratedLaunchSpeed(discImpl);
        double turnFactor = smoothRamp(speedRatio, .55, .96) * (1 - smoothRamp(tick, 10, 31));
        double fadeFactor = (1 - smoothRamp(speedRatio, .42, .83)) * smoothRamp(tick, 5, 27)
                * speedRatio * speedRatio;
        double turn = discImpl.getTurn() * turnFactor * .0115 * turnMultiplier;
        double fade = Math.min(.032, discImpl.getFade() * fadeFactor * .060) * fadeMultiplier;
        this.applyTechniquePhysics(discImpl, discVelocity, tick, new TechniquePhysicsData(flightPhase, turn, fade), direction);
    }

    public abstract void applyTechniquePhysics(DiscImpl discImpl, Vector discVelocity, int tick, TechniquePhysicsData techniquePhysicsData, BlockFace direction);

    protected static void applyStandardFlight(DiscImpl disc, Vector velocity, TechniquePhysicsData physics, int bendSign) {
        if (physics.flightPhase() > Constants.GRAVITY_PHASE_START) {
            velocity.setY(velocity.getY() - gravity(disc));
        }
        // Positive Y rotation bends a backhand left. Negative turn bends it right.
        velocity.rotateAroundY(bendSign * (physics.turnRadians() + physics.fadeRadians()));
    }

    private static double smoothRamp(double value, double start, double end) {
        double progress = Math.clamp((value - start) / (end - start), 0, 1);
        return progress * progress * (3 - 2 * progress);
    }

    protected static double gravity(DiscImpl disc) {
        double baseline = DiscModel.starter(disc.getType()).glide();
        return (baseline / 220) * (baseline / Math.max(1, disc.getGlide()));
    }
}
