package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import normalmanv2.normalDiscGolf.api.NDGApi;
import normalmanv2.normalDiscGolf.impl.course.Course;
import normalmanv2.normalDiscGolf.impl.course.CourseDifficulty;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.impl.round.RoundHandler;
import normalmanv2.normalDiscGolf.api.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TestFFARound implements CommandExecutor {

    private final NormalDiscGolf plugin;
    private final RoundHandler roundHandler;

    public TestFFARound(NDGApi ndgApi, NormalDiscGolf plugin) {
        this.roundHandler = ndgApi.getRoundHandler();
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {

        final GameRound round;

        if (!(commandSender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            return false;
        }

        if (args[0].equalsIgnoreCase("start")) {
            round = new FFARound(plugin, NDGApi.getInstance().getPlayerDataManager(), new Course(CourseDifficulty.EASY, "Test Course", 18, new Location(player.getWorld(), 19, 68, -107)), false);
            for (Player player1 : Bukkit.getOnlinePlayers()) {
                Team team = new Team(player1.getUniqueId());
                round.addTeam(team);
            }
            roundHandler.startRound(round);
        } else if (args[0].equalsIgnoreCase("end")) {
            for (GameRound foundRound : roundHandler.getActiveRounds()) {
                roundHandler.endRound(foundRound);
            }
        }

        return true;
    }
}
