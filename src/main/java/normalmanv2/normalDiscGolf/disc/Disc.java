package normalmanv2.normalDiscGolf.disc;

import normalmanv2.normalDiscGolf.attribute.PlayerAttribute;
import normalmanv2.normalDiscGolf.technique.ThrowTechnique;
import org.bukkit.entity.Player;

public abstract class Disc {
//
    private final int speed;
    private final int glide;
    private final double turn;
    private final double fade;
    private final DiscType type;

    public Disc(int speed, int glide, double turn, double fade, DiscType type) {
        this.speed = speed;
        this.glide = glide;
        this.turn = turn;
        this.fade = fade;
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

    public DiscType getType() {
        return this.type;
    }

    public abstract void handleThrow(Player player, PlayerAttribute attribute, ThrowTechnique technique);

}
