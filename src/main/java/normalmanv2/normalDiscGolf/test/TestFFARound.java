package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.CourseDifficulty;
import normalmanv2.normalDiscGolf.impl.course.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.TileTypes;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.impl.manager.round.lifecycle.RoundHandler;
import normalmanv2.normalDiscGolf.impl.team.TeamImpl;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TestFFARound implements CommandExecutor {

    private final NormalDiscGolf plugin;
    private final RoundHandler roundHandler;

    public TestFFARound(NDGManager ndgManager, NormalDiscGolf plugin) {
        this.roundHandler = ndgManager.getRoundHandler();
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        /*
        final GameRound round;

        if (!(commandSender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            return false;
        }

        if (args[0].equalsIgnoreCase("start")) {
            List<TileTypes> tileTypes = Arrays.asList(TileTypes.values());
            World world = Bukkit.getWorld("Test_Course");
            CourseGrid courseGrid = new CourseGrid(8, 8, tileTypes, world);
            round = new FFARound(plugin, NDGManager.getInstance().getPlayerDataManager(), new CourseImpl(CourseDifficulty.EASY, "Test Course", 18, courseGrid, world.getSpawnLocation(), Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap()), false, "ffa", 8);
            for (Player player1 : Bukkit.getOnlinePlayers()) {
                TeamImpl teamImpl = new TeamImpl(player1.getUniqueId(), 1);
                round.addTeam(teamImpl);
            }
            roundHandler.startRound(round);
            System.out.println("Round should be started now??");
        } else if (args[0].equalsIgnoreCase("end")) {
            for (GameRound foundRound : roundHandler.getActiveRounds()) {
                roundHandler.endRound(foundRound);
                System.out.println("Round should be ended now??");
            }
        }

        return true;
    }
         */
    return false;
    }
}
