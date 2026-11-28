package normalmanv2.normalDiscGolf.impl.command;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.common.command.AbstractCommand;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.disc.ArcadeDisc;
import normalmanv2.normalDiscGolf.impl.listener.RoundPlayerListener;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import normalmanv2.normalDiscGolf.impl.round.ArcadeRound;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

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
            if (NDGManager.getInstance().getClubhouse().aim(player, args)) return;
            player.sendMessage(ChatColor.RED + "You are not in an active disc golf round.");
            return;
        }

        var play = ((RoundImpl) round).play();
        String error = play.validate(player);
        if (error != null) {
            player.sendMessage(ChatColor.RED + error);
            return;
        }

        var packed = play.throwableDiscs(player.getUniqueId());
        String requested = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        DiscModel held = requested.isEmpty() ? RoundPlayerListener.heldDisc(player.getInventory().getItemInMainHand()) : null;
        ArcadeDisc special = ArcadeDisc.find(held == null ? requested : held.id());
        if (special != null) {
            if (!(round instanceof ArcadeRound arcade)) {
                player.sendMessage(ChatColor.RED + "Special discs are available in Arcade rounds.");
                return;
            }
            if (arcade.remaining(player.getUniqueId(), special) == 0) {
                player.sendMessage(ChatColor.RED + special.model().displayName() + " has no charges left this round.");
                return;
            }
        }
        DiscType type = switch (requested) {
            case "driver" -> DiscType.DRIVER;
            case "mid", "midrange" -> DiscType.MID_RANGE;
            case "putter" -> DiscType.PUTTER;
            default -> null;
        };
        DiscModel model = NDGManager.getInstance().getDiscCatalog().find(requested);
        if (type != null) model = packed.stream().filter(disc -> disc.type() == type).findFirst().orElse(null);
        if (requested.isEmpty() && !packed.isEmpty()) {
            model = held != null && packed.contains(held) ? held : packed.get(0);
        }
        if (special != null) model = special.model();
        else if (model == null || !packed.contains(model)) {
            player.sendMessage(ChatColor.RED + play.discRestriction(player.getUniqueId()));
            return;
        }
        String discName = model.displayName();
        String technique = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "backhand";

        if (!NDGManager.getInstance().getThrowTechniqueRegistry().getBackingMap().containsKey(technique)) {
            player.sendMessage(ChatColor.RED + "Unknown throw technique. Use backhand or forehand.");
            return;
        }

        try {
            DiscImpl disc = special == null ? NDGManager.getInstance().getDiscRegistry().getDiscByName(discName) : model.createDisc();
            new ThrowMechanicImpl(player.getUniqueId(), this.plugin, disc, round, technique, special);
            player.sendMessage(ChatColor.GREEN + "Throw meter started for " + discName + " (" + technique + ").");
        } catch (CloneNotSupportedException exception) {
            player.sendMessage(ChatColor.RED + "That disc could not be prepared for throwing.");
        } catch (RuntimeException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender instanceof Player player && findActiveRound(player) instanceof RoundImpl round) {
            var ordinary = round.play().throwableDiscs(player.getUniqueId()).stream().map(DiscModel::id);
            if (round instanceof ArcadeRound arcade)
                return Stream.concat(ordinary, ArcadeDisc.all().stream().filter(disc -> arcade.remaining(player.getUniqueId(), disc) > 0)
                        .map(disc -> disc.model().id())).toList();
            return ordinary.toList();
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

}
