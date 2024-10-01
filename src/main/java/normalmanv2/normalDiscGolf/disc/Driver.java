package normalmanv2.normalDiscGolf.disc;

import normalmanv2.normalDiscGolf.attribute.PlayerAttribute;
import normalmanv2.normalDiscGolf.technique.ThrowTechnique;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class Driver extends Disc {

    public Driver(int speed, int glide, int turn, int fade){
        super(speed, glide, turn, fade, DiscType.DRIVER);
    }

    public void handleThrow(Player player, PlayerAttribute attributes, ThrowTechnique technique) {

        Location throwLoc = player.getLocation();
        Vector direction = player.getEyeLocation().getDirection().normalize();

        switch (technique) {

            case BACKHAND -> {


            }
            case FOREHAND -> {


            }
            case FLEX -> {


            }
            case ROLLER -> {


            }
            case S_CURVE -> {


            }
            case THUMBER -> {


            }
            case TOMAHAWK -> {


            }

        }

    }

}
