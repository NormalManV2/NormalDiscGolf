package normalmanv2.normalDiscGolf.api.disc;

import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public abstract class Disc implements Cloneable {

    private final int speed;
    private final int glide;
    private final double turn;
    private final double fade;
    private final String discName;
    private final DiscType type;

    public Disc(int speed, int glide, double turn, double fade, String discName, DiscType type) {
        this.speed = speed;
        this.glide = glide;
        this.turn = turn;
        this.fade = fade;
        this.discName = discName;
        this.type = type;
    }

    public int getSpeed() {
        return this.speed;
    }

    public int getGlide() {
        return this.glide;
    }

    public double getTurn() {
        return this.turn;
    }

    public double getFade() {
        return this.fade;
    }

    public String getDiscName() {
        return this.discName;
    }

    public DiscType getType() {
        return this.type;
    }

    public Disc clone() throws CloneNotSupportedException {
        return (Disc) super.clone();
    }

    public abstract void handleThrow(Player player, PlayerSkills skills, String technique, BlockFace direction);

    public abstract void applyDiscPhysics(Player player, ItemDisplay discDisplay, Vector initialVelocity, int maxTicks, String technique, BlockFace direction);

}
