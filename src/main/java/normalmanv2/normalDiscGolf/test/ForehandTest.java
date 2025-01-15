package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.registry.DiscRegistry;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.impl.manager.round.lifecycle.RoundHandler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ForehandTest implements CommandExecutor {

    private final DiscRegistry discRegistry;
    private final RoundHandler roundHandler;
    private GameRound round;

    public ForehandTest(NDGManager ndgManager) {
        this.discRegistry = ndgManager.getDiscRegistry();
        this.roundHandler = ndgManager.getRoundHandler();
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {

        if (!(commandSender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            return false;
        }

        UUID playerId = player.getUniqueId();

        for (GameRound round : roundHandler.getActiveRounds()) {
            for (Team team : round.getTeams()) {
                if (team.getTeamMembers().contains(playerId)) {
                    this.round = round;
                }
            }
        }

        try {
            round.handleStroke(playerId, "forehand", discRegistry.getDiscByString(args[0]));
        } catch (CloneNotSupportedException exception) {
            exception.printStackTrace();
            return false;
        }

        return true;
    }
}
