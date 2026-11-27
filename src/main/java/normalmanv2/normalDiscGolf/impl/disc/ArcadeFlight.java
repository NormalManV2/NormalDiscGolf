package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.impl.disc.util.MathUtil.FlightHit;
import normalmanv2.normalDiscGolf.impl.disc.util.MathUtil.HitType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

/** State for one live flight or one preview simulation. */
public final class ArcadeFlight {
    private final ArcadeDisc disc;
    private Block phasedBlock;
    private ArcadeDisc effect;

    public ArcadeFlight(ArcadeDisc disc) { this.disc = disc; }

    void beginStep(Vector velocity, int tick, boolean airborne) {
        effect = null;
        if (disc == ArcadeDisc.ROCKET && tick == 5 && airborne) {
            velocity.setX(velocity.getX() * 1.42);
            velocity.setZ(velocity.getZ() * 1.42);
            velocity.setY(velocity.getY() + .10);
            effect = disc;
        }
    }

    public boolean canPhase() { return disc == ArcadeDisc.PHANTOM && phasedBlock == null; }
    public Block phasedBlock() { return phasedBlock; }

    public void phase(Block block) {
        if (!canPhase()) return;
        phasedBlock = block;
        effect = disc;
    }

    boolean sticks(FlightHit hit) {
        if (disc != ArcadeDisc.STICKY || hit.type() != HitType.BLOCK || hit.face() != BlockFace.UP) return false;
        effect = disc;
        return true;
    }

    ArcadeDisc effect() { return effect; }
}
