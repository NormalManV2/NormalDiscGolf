package normalmanv2.normalDiscGolf.impl.technique;

import normalmanv2.normalDiscGolf.impl.disc.Disc;
import normalmanv2.normalDiscGolf.impl.technique.data.TechniquePhysicsData;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public abstract class ThrowTechnique {
    private final String technique;

    public ThrowTechnique(String technique) {
        this.technique = technique;
    }

    public String getTechnique() {
        return this.technique;
    }

    public void applyPhysics(Disc disc, Vector discVelocity, int tick, int maxTicks, BlockFace direction) {
        final double flightPhase = (double) tick / (double) maxTicks;

        // Diminishing factor of the turn phase
        final double turnFactor = (Constants.TURN_PHASE_END - flightPhase) / Constants.TURN_PHASE_END;
        // Exponential increase of the fade phase - scale the last number for increase / decrease of the exponential effect
        final double fadeFactor = Math.pow((flightPhase - Constants.FADE_PHASE_START) / (1 - Constants.FADE_PHASE_START), 3);
        // Drag factor
        discVelocity.multiply(Constants.DRAG_FACTOR);

        this.applyTechniquePhysics(disc, discVelocity, tick, new TechniquePhysicsData(flightPhase, turnFactor, fadeFactor), direction);
    }

    public abstract void applyTechniquePhysics(Disc disc, Vector discVelocity, int tick, TechniquePhysicsData techniquePhysicsData, BlockFace direction);
}
