package normalmanv2.normalDiscGolf.impl.technique;

import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
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
        final double flightPhase = (double) tick / (double) maxTicks;

        // Diminishing factor of the turn phase
        final double turnFactor = (Constants.TURN_PHASE_END - flightPhase) / Constants.TURN_PHASE_END;
        // Exponential increase of the fade phase - scale the last number for increase / decrease of the exponential effect
        final double fadeFactor = Math.pow((flightPhase - Constants.FADE_PHASE_START) / (1 - Constants.FADE_PHASE_START), 3);
        // Drag factor
        discVelocity.multiply(Constants.DRAG_FACTOR);

        this.applyTechniquePhysics(discImpl, discVelocity, tick, new TechniquePhysicsData(flightPhase, turnFactor, fadeFactor), direction);
    }

    public abstract void applyTechniquePhysics(DiscImpl discImpl, Vector discVelocity, int tick, TechniquePhysicsData techniquePhysicsData, BlockFace direction);
}
