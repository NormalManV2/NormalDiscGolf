package normalmanv2.normalDiscGolf.impl.technique;

import normalmanv2.normalDiscGolf.impl.disc.Disc;
import normalmanv2.normalDiscGolf.impl.technique.data.TechniquePhysicsData;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.util.Vector;

public abstract class ThrowTechnique {
    private final String technique;

    public ThrowTechnique(String technique) {
        this.technique = technique;
    }

    public String getTechnique() {
        return this.technique;
    }

    public void applyPhysics(Disc disc, Vector discVelocity, int tick, int maxTicks) {
        final double flightPhase = (double) tick / (double) maxTicks;

        // final double gravityFactor = (double) disc.getGlide() / 200;
        // final double liftFactor = gravityFactor + (Math.sin(tick * 0.1) + 0.001);

        // Diminishing factor of the turn phase
        final double turnFactor = (Constants.TURN_PHASE_END - flightPhase) / Constants.TURN_PHASE_END;
        // Exponential increase of the fade phase - scale the last number for increase / decrease of the exponential effect
        final double fadeFactor = Math.pow((flightPhase - Constants.FADE_PHASE_START) / (1 - Constants.FADE_PHASE_START), 3);

        discVelocity.multiply(0.98);

        // discVelocity.setY(discVelocity.getY() - gravityFactor);
        this.applyTechniquePhysics(disc, discVelocity, tick, new TechniquePhysicsData(flightPhase, turnFactor, fadeFactor));
        discVelocity.setZ(discVelocity.getZ() + (Math.sin(tick * 0.1) * 0.01));
    }

    public abstract void applyTechniquePhysics(Disc disc, Vector discVelocity, int tick, TechniquePhysicsData techniquePhysicsData);
}
