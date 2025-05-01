package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.*;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.course.world.CourseGridWorldCreator;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class WFCTest implements CommandExecutor {
    private final NormalDiscGolfPlugin plugin;

    public WFCTest(NormalDiscGolfPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {

        Player player = (Player) commandSender;
        UUID playerId = player.getUniqueId();
        Division division = NDGManager.getInstance().getPlayerDataManager().getDataByPlayer(playerId).getRating().getDivision();

        CourseGrid grid = new CourseGrid(Constants.DEFAULT_FFA_COURSE_SIZE, Constants.DEFAULT_FFA_COURSE_SIZE, List.of(TileTypes.values()), Constants.DEFAULT_COURSE_HOLES);

        CourseImpl course = new CourseCreator().create(division, grid, player.getDisplayName() + "." + playerId);

        WorldCreator worldCreator = new CourseGridWorldCreator(player.getDisplayName() + "." + playerId, course, division);

        World world = Bukkit.createWorld(worldCreator);

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            grid.generateObstacles(world);
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> player.teleport(world.getSpawnLocation()), 20);
        }, 20);

    return true;
    }
}
