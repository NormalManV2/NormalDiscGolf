package normalmanv2.normalDiscGolf.api.mechanic;

import normalmanv2.normalDiscGolf.api.disc.Disc;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.util.UUID;

public interface ThrowMechanic {

    BlockFace getDirection();
    Location getThrowLocation();
    UUID getPlayerId();
    Disc getDisc();
    String getThrowTechnique();
}
