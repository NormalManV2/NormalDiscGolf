package normalmanv2.normalDiscGolf.api.mechanic;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.UUID;

public class ThrowMechanicImpl implements ThrowMechanic {

    private final Vector direction;
    private final Location throwLocation;
    private final int accuracy;
    private final int form;
    private final int power;
    private final UUID playerId;

    public ThrowMechanicImpl(Vector direction, Location throwLocation, int accuracy, int form, int power, UUID playerId) {
        this.direction = direction;
        this.throwLocation = throwLocation;
        this.accuracy = accuracy;
        this.form = form;
        this.power = power;
        this.playerId = playerId;
    }

    @Override
    public Vector getDirection() {
        return this.direction;
    }

    @Override
    public Location getThrowLocation() {
        return this.throwLocation;
    }

    @Override
    public int getAccuracy() {
        return this.accuracy;
    }

    @Override
    public int getFormLevel() {
        return this.form;
    }

    @Override
    public int powerLevel() {
        return this.power;
    }

    @Override
    public UUID getPlayerId() {
        return this.playerId;
    }
}
