package normalmanv2.normalDiscGolf.technique.type;

import normalmanv2.normalDiscGolf.disc.Disc;
import normalmanv2.normalDiscGolf.technique.ThrowTechnique;
import normalmanv2.normalDiscGolf.technique.data.TechniquePhysicsData;
import normalmanv2.normalDiscGolf.util.Constants;
import org.bukkit.util.Vector;

public class ForehandTechnique extends ThrowTechnique {
    public ForehandTechnique() {
        super("forehand");
    }

    @Override
    public void applyTechniquePhysics(Disc disc, Vector discVelocity, int tick, TechniquePhysicsData techniquePhysicsData) {
        final double turn = disc.getTurn();
        final double flightPhase = techniquePhysicsData.getFlightPhase();
        final double turnFactor = techniquePhysicsData.getTurnFactor();
        final double fadeFactor = techniquePhysicsData.getFadeFactor();

        if (flightPhase <= Constants.TURN_PHASE_END) {
            final double turnAdjustment = turn * turnFactor * 0.2;
            discVelocity.setX(discVelocity.getX() + turnAdjustment);
        }

        if (flightPhase >= Constants.FADE_PHASE_START) {
            final double fadeAdjustment = turn * fadeFactor * 0.15;
            discVelocity.setX(discVelocity.getX() - fadeAdjustment);
        }
    }
}
