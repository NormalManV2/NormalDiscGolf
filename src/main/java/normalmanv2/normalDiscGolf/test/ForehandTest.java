package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.impl.registry.DiscRegistry;
import normalmanv2.normalDiscGolf.api.NDGApi;
import normalmanv2.normalDiscGolf.impl.round.GameRound;
import normalmanv2.normalDiscGolf.impl.round.RoundHandler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ForehandTest implements CommandExecutor {

    private final DiscRegistry discRegistry;
    private final RoundHandler roundHandler;
    private GameRound round;

    public ForehandTest(NDGApi ndgApi) {
        this.discRegistry = ndgApi.getDiscRegistry();
        this.roundHandler = ndgApi.getRoundHandler();
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
            if (round.getPlayers().contains(playerId)) {
                this.round = round;
                break;
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
