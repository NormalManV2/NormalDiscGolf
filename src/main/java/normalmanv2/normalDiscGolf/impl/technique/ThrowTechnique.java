package normalmanv2.normalDiscGolf.impl.technique;

import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
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
        final double flightPhase = (double) tick / (double) maxTicks;

        // Diminishing factor of the turn phase
        final double turnFactor = (Constants.TURN_PHASE_END - flightPhase) / Constants.TURN_PHASE_END;
        // Exponential increase of the fade phase
        final double fadeFactor = Math.pow((flightPhase - Constants.FADE_PHASE_START) / (1 - Constants.FADE_PHASE_START), 3) * fadeMultiplier;
        // Drag factor
        discVelocity.multiply(drag);

        this.applyTechniquePhysics(discImpl, discVelocity, tick, new TechniquePhysicsData(flightPhase, turnFactor, fadeFactor), direction);
    }

    public abstract void applyTechniquePhysics(DiscImpl discImpl, Vector discVelocity, int tick, TechniquePhysicsData techniquePhysicsData, BlockFace direction);

    protected static double gravity(DiscImpl disc) {
        double baseline = DiscModel.starter(disc.getType()).glide();
        return (baseline / 220) * (baseline / Math.max(1, disc.getGlide()));
    }
}
