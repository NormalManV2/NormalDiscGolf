package normalmanv2.normalDiscGolf.impl.technique.type;

import normalmanv2.normalDiscGolf.impl.disc.Disc;
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
        final double flightPhase = techniquePhysicsData.flightPhase();
        final double turnFactor = techniquePhysicsData.turnFactor();
        final double fadeFactor = techniquePhysicsData.fadeFactor();
        final double gravityFactor = (double) disc.getGlide() / 220;
        final double turnAdjustment = turn * turnFactor * 0.15;
        final double fadeAdjustment = turn * fadeFactor * 0.25;

        if (flightPhase <= Constants.STRAIGHT_PHASE_END) {
            return;
        }

        discVelocity.setY(discVelocity.getY() - gravityFactor);

        switch (direction) {
            case NORTH -> {
                if (flightPhase <= Constants.TURN_PHASE_END) {

                    discVelocity.setX(discVelocity.getX() + turnAdjustment);
                }
                if (flightPhase >= Constants.FADE_PHASE_START) {

                    discVelocity.setX(discVelocity.getX() - fadeAdjustment);
                }
            }

            case SOUTH -> {
                if (flightPhase <= Constants.TURN_PHASE_END) {

                    discVelocity.setX(discVelocity.getX() - turnAdjustment);
                }
                if (flightPhase >= Constants.FADE_PHASE_START) {

                    discVelocity.setX(discVelocity.getX() + fadeAdjustment);
                }
            }

            case WEST -> {
                if (flightPhase <= Constants.TURN_PHASE_END) {

                    discVelocity.setZ(discVelocity.getZ() - turnAdjustment);
                }
                if (flightPhase >= Constants.FADE_PHASE_START) {

                    discVelocity.setZ(discVelocity.getZ() + fadeAdjustment);
                }
            }

            case EAST -> {
                if (flightPhase <= Constants.TURN_PHASE_END) {

                    discVelocity.setZ(discVelocity.getZ() + turnAdjustment);
                }
                if (flightPhase >= Constants.FADE_PHASE_START) {

                    discVelocity.setZ(discVelocity.getZ() - fadeAdjustment);
                }
            }
        }
    }
}
