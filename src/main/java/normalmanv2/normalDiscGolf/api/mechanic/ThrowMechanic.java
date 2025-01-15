package normalmanv2.normalDiscGolf.api.mechanic;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.UUID;

public interface ThrowMechanic {

    Vector getDirection();
    Location getThrowLocation();
    int getAccuracy();
    int getFormLevel();
    int powerLevel();
    UUID getPlayerId();
}
