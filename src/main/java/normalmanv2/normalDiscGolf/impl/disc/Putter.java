package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class Putter extends DiscImpl {
    public Putter(int speed, int glide, int turn, int fade, String discName) {
        super(speed, glide, turn, fade, discName, DiscType.PUTTER);
    }

    @Override
    public void handleThrow(Player player, PlayerSkills skills, String technique, BlockFace facing, ThrowMechanic mechanic) {
        DiscFlightEngine.launch(player, skills, (ThrowMechanicImpl) mechanic);
    }

    @Override
    public void applyDiscPhysics(Player player, ItemDisplay display, Vector velocity, int maxTicks, String technique, BlockFace facing) {
        DiscFlightEngine.start(this, player, display, velocity, maxTicks, technique, facing);
    }
}
