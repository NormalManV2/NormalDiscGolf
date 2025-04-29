package normalmanv2.normalDiscGolf.impl.technique.type;

import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
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
    public void applyTechniquePhysics(DiscImpl discImpl, Vector discVelocity, int tick, TechniquePhysicsData techniquePhysicsData, BlockFace direction) {
        final double turn = discImpl.getTurn();
        final double fade = discImpl.getFade();
        final double flightPhase = techniquePhysicsData.flightPhase();
        final double turnFactor = techniquePhysicsData.turnFactor();
        final double fadeFactor = techniquePhysicsData.fadeFactor();
        final double gravityFactor = (double) discImpl.getGlide() / 220;
        final double turnAdjustment = turn * turnFactor * Constants.TURN_ADJUSTMENT;
        final double fadeAdjustment = fade * fadeFactor * Constants.FADE_ADJUSTMENT;

        // Early return to keep the disc flying straight for a period of time.
        if (flightPhase <= Constants.STRAIGHT_PHASE_END) {
            return;
        }

        // Gravity factor
        discVelocity.setY(discVelocity.getY() - gravityFactor);

        Vector currentDirection = new Vector(discVelocity.getX(), 0, discVelocity.getZ()).normalize();
        Vector leftwardDrift = new Vector(-currentDirection.getZ(), 0, currentDirection.getX()).normalize();

        // "Turn" physics of the disc mid flight
        if (flightPhase <= Constants.TURN_PHASE_END) {
            discVelocity.add(leftwardDrift.clone().multiply(turnAdjustment));
        }
        // "Fade" physics of the disc at the end of flight
        if (flightPhase >= Constants.FADE_PHASE_START) {
            discVelocity.subtract(leftwardDrift.clone().multiply(fadeAdjustment));
        }
    }
}
