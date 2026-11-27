package normalmanv2.normalDiscGolf.impl.command;

import normalmanv2.normalDiscGolf.impl.disc.ShotSetup;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class ShotCommand {
    private ShotCommand() {}

    static void execute(Player player, RoundImpl round, String[] args) {
        var play = round.play();
        ShotSetup current = play.setup(player.getUniqueId());
        if (args.length == 1) {
            show(player, current);
            player.sendMessage(ChatColor.GRAY + "/ndg shot stance <" + String.join("|", values("stance")) + ">");
            player.sendMessage(ChatColor.GRAY + "/ndg shot angle <flat|hyzer|anhyzer>, timing <on|off>, preview <on|off>, wind");
            return;
        }
        String option = args[1].toLowerCase(Locale.ROOT);
        if (option.equals("wind") && args.length == 2) {
            var personal = NDGManager.getInstance().getPlayerDataManager().getOrCreatePlayerData(player.getUniqueId()).getSkills();
            player.sendMessage(ChatColor.AQUA + play.wind().description(play.effectiveSkills(personal).getWindReading().getLevel()));
            return;
        }
        if (args.length != 3) {
            player.sendMessage(ChatColor.RED + "Use /ndg shot for the available settings.");
            return;
        }
        String value = args[2].toLowerCase(Locale.ROOT);
        if (!values(option).contains(value)) {
            player.sendMessage(ChatColor.RED + "Use /ndg shot for the available settings.");
            return;
        }
        ShotSetup next = switch (option) {
            case "angle" -> current.withAngle(ShotSetup.Angle.valueOf(value.toUpperCase(Locale.ROOT)));
            case "stance" -> new ShotSetup(current.angle(), ShotSetup.Stance.valueOf(value.toUpperCase(Locale.ROOT)), current.timing(), current.preview());
            case "timing" -> new ShotSetup(current.angle(), current.stance(), value.equals("on"), current.preview());
            case "preview" -> new ShotSetup(current.angle(), current.stance(), current.timing(), value.equals("on"));
            default -> throw new IllegalArgumentException(option);
        };
        if (!next.equals(current)) play.setSetup(player.getUniqueId(), next);
        show(player, next);
    }

    static List<String> complete(String[] args) {
        return switch (args.length) {
            case 2 -> List.of("angle", "stance", "timing", "preview", "wind");
            case 3 -> values(args[1].toLowerCase(Locale.ROOT));
            default -> List.of();
        };
    }

    private static List<String> values(String option) {
        return switch (option) {
            case "angle" -> List.of("flat", "hyzer", "anhyzer");
            case "stance" -> Arrays.stream(ShotSetup.Stance.values()).map(ShotSetup.Stance::id).toList();
            case "timing", "preview" -> List.of("on", "off");
            default -> List.of();
        };
    }

    private static void show(Player player, ShotSetup setup) {
        player.sendMessage(ChatColor.AQUA + "Shot setup · " + setup.angle().name().toLowerCase(Locale.ROOT)
                + " · " + setup.stance().id() + " stance · timing "
                + (setup.timing() ? "on" : "off") + " · preview " + (setup.preview() ? "on" : "off"));
    }
}
