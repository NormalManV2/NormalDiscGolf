package normalmanv2.normalDiscGolf.common.disc;

import normalmanv2.normalDiscGolf.api.disc.Disc;
import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public abstract class DiscImpl implements Disc {

    private final int speed;
    private final int glide;
    private final double turn;
    private final double fade;
    private final String discName;
    private final DiscType type;

    public DiscImpl(int speed, int glide, double turn, double fade, String discName, DiscType type) {
        this.speed = speed;
        this.glide = glide;
        this.turn = turn;
        this.fade = fade;
        this.discName = discName;
        this.type = type;
    }

    @Override
    public int getSpeed() {
        return this.speed;
    }

    @Override
    public int getGlide() {
        return this.glide;
    }

    @Override
    public double getTurn() {
        return this.turn;
    }

    @Override
    public double getFade() {
        return this.fade;
    }

    @Override
    public String getName() {
        return this.discName;
    }

    @Override
    public DiscType getType() {
        return this.type;
    }

    public DiscImpl clone() throws CloneNotSupportedException {
        return (DiscImpl) super.clone();
    }

    public abstract void handleThrow(Player player, PlayerSkills skills, String technique, BlockFace direction, ThrowMechanic throwMechanic);

    public abstract void applyDiscPhysics(Player player, ItemDisplay discDisplay, Vector initialVelocity, int maxTicks, String technique, BlockFace direction);

}
