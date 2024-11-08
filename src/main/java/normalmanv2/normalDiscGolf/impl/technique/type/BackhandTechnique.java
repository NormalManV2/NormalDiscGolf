package normalmanv2.normalDiscGolf.impl.technique.type;

import normalmanv2.normalDiscGolf.api.disc.Disc;
import normalmanv2.normalDiscGolf.impl.technique.ThrowTechnique;
import normalmanv2.normalDiscGolf.impl.technique.data.TechniquePhysicsData;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public class BackhandTechnique extends ThrowTechnique {
    public BackhandTechnique() {
        super("backhand");
    }

    @Override
    public void applyTechniquePhysics(Disc disc, Vector discVelocity, int tick, TechniquePhysicsData techniquePhysicsData, BlockFace direction) {
        final double turn = disc.getTurn();
        final double fade = disc.getFade();
        final double flightPhase = techniquePhysicsData.flightPhase();
        final double turnFactor = techniquePhysicsData.turnFactor();
        final double fadeFactor = techniquePhysicsData.fadeFactor();
        final double gravityFactor = (double) disc.getGlide() / 220;
        final double turnAdjustment = turn * turnFactor * Constants.TURN_ADJUSTMENT;
        final double fadeAdjustment = fade * fadeFactor * Constants.FADE_ADJUSTMENT;

        if (flightPhase <= Constants.STRAIGHT_PHASE_END) {
            return;
        }

        discVelocity.setY(discVelocity.getY() - gravityFactor);

        Vector currentDirection = new Vector(discVelocity.getX(), 0, discVelocity.getZ()).normalize();
        Vector leftwardDrift = new Vector(-currentDirection.getZ(), 0, currentDirection.getX()).normalize();
        if (flightPhase <= Constants.TURN_PHASE_END) {
            discVelocity.add(leftwardDrift.clone().multiply(turnAdjustment));
        }
        if (flightPhase >= Constants.FADE_PHASE_START) {
            discVelocity.subtract(leftwardDrift.clone().multiply(fadeAdjustment));
        }
    }
}
