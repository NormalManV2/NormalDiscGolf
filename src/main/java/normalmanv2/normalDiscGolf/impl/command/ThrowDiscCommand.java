package normalmanv2.normalDiscGolf.impl.command;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.command.AbstractCommand;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class ThrowDiscCommand extends AbstractCommand {

    private final Plugin plugin;

    public ThrowDiscCommand(String command, String[] aliases, String description, String permission, Plugin plugin) {
        super(command, aliases, description, permission);
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can throw discs.");
            return;
        }

        GameRound round = this.findActiveRound(player);
        if (round == null) {
            player.sendMessage(ChatColor.RED + "You are not in an active disc golf round.");
            return;
        }

        if (!this.isPlayersTurn(round, player)) {
            player.sendMessage(ChatColor.RED + "It is not your turn to throw.");
            return;
        }

        String discName = this.resolveDiscName(args.length > 0 ? args[0] : "putter");
        String technique = args.length > 1 ? args[1].toLowerCase() : "backhand";

        if (!NDGManager.getInstance().getThrowTechniqueRegistry().getBackingMap().containsKey(technique)) {
            player.sendMessage(ChatColor.RED + "Unknown throw technique. Use backhand or forehand.");
            return;
        }

        try {
            DiscImpl disc = NDGManager.getInstance().getDiscRegistry().getDiscByName(discName);
            new ThrowMechanicImpl(player.getUniqueId(), this.plugin, disc, round, technique);
            player.sendMessage(ChatColor.GREEN + "Throw meter started for " + discName + " (" + technique + ").");
        } catch (CloneNotSupportedException exception) {
            player.sendMessage(ChatColor.RED + "That disc could not be prepared for throwing.");
        } catch (RuntimeException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("putter", "midrange", "driver");
        }

        if (args.length == 2) {
            return List.of("backhand", "forehand");
        }

        return List.of();
    }

    private GameRound findActiveRound(Player player) {
        return NDGManager.getInstance().getRoundHandler().getActiveRounds().stream()
                .filter(round -> round.containsPlayer(player.getUniqueId()))
                .findFirst()
                .orElse(null);
    }

    private boolean isPlayersTurn(GameRound round, Player player) {
        for (Team team : round.getTeams()) {
            if (team.contains(player.getUniqueId())) {
                return round.isTurn(team);
            }
        }

        return false;
    }

    private String resolveDiscName(String requestedDisc) {
        return switch (requestedDisc.toLowerCase()) {
            case "driver" -> "Starter Driver";
            case "mid", "midrange" -> "Starter Midrange";
            default -> "Starter Putter";
        };
    }
}
