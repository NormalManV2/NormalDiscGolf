package normalmanv2.normalDiscGolf.impl.technique.type;

import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.technique.ThrowTechnique;
import normalmanv2.normalDiscGolf.impl.technique.data.TechniquePhysicsData;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public class ForehandTechnique extends ThrowTechnique {
    public ForehandTechnique() {
        super("forehand");
    }

    @Override
    public void applyTechniquePhysics(DiscImpl discImpl, Vector discVelocity, int tick, TechniquePhysicsData techniquePhysicsData, BlockFace direction) {
        applyStandardFlight(discImpl, discVelocity, techniquePhysicsData, -1);
    }
}
