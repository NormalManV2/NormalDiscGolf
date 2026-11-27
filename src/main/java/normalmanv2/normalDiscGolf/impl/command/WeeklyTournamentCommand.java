package normalmanv2.normalDiscGolf.impl.command;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.competition.StrokeStandings;
import normalmanv2.normalDiscGolf.impl.competition.WeeklyTournament;
import normalmanv2.normalDiscGolf.impl.competition.WeeklyTournaments;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

final class WeeklyTournamentCommand {
    private WeeklyTournamentCommand() {}

    static void execute(Player player, String[] args) {
        var tournaments = NDGManager.getInstance().getWeeklyTournaments();
        String action = args.length == 1 ? "status" : args[1].toLowerCase(Locale.ROOT);
        try {
            if (action.equals("standings") && args.length <= 3) {
                standings(player, tournaments.standings(args.length == 3 ? LocalDate.parse(args[2]) : null));
            } else if (args.length <= 2) {
                switch (action) {
                    case "status" -> status(player, tournaments);
                    case "signup", "withdraw" -> player.sendMessage(ChatColor.AQUA + tournaments.signUp(player, action.equals("signup")));
                    default -> help(player);
                }
            } else help(player);
        } catch (DateTimeParseException invalid) {
            player.sendMessage(ChatColor.RED + "Use /ndg tournament standings with a YYYY-MM-DD date.");
        } catch (IOException failure) {
            NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger()
                    .log(Level.WARNING, "Weekly tournament command failed", failure);
            player.sendMessage(ChatColor.RED + "Weekly tournament data is currently unavailable.");
        }
    }

    private static void status(Player player, WeeklyTournaments tournaments) throws IOException {
        var recent = tournaments.standings(null);
        if (recent != null && recent.status() == WeeklyTournament.Status.RUNNING)
            player.sendMessage(ChatColor.GOLD + "The weekly tournament for " + recent.definition().date() + " is in progress.");
        var next = tournaments.upcoming();
        if (next == null) {
            player.sendMessage(ChatColor.YELLOW + "Weekly tournament registration is disabled.");
            return;
        }
        var definition = next.definition();
        player.sendMessage(ChatColor.GOLD + "Weekly tournament starts: " + definition.start());
        player.sendMessage(ChatColor.GRAY + "Registration " + (next.registrationOpen(tournaments.now()) ? "is open"
                : "opens " + definition.opens()) + " • " + next.entrants().size() + "/" + definition.capacity()
                + " signed up • " + definition.holes() + (definition.holes() == 1 ? " hole" : " holes"));
        if (next.entrants().containsKey(player.getUniqueId())) player.sendMessage(ChatColor.GREEN + "You are signed up.");
        help(player);
    }

    private static void standings(Player player, WeeklyTournament event) {
        if (event == null) {
            player.sendMessage(ChatColor.YELLOW + "No weekly tournament standings are available yet.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "Weekly tournament standings - " + event.definition().date() + " UTC");
        switch (event.status()) {
            case SCHEDULED -> player.sendMessage(ChatColor.GRAY + "This event starts " + event.definition().start() + ".");
            case RUNNING -> player.sendMessage(ChatColor.GRAY + "The tournament is in progress.");
            case CANCELED -> player.sendMessage(ChatColor.YELLOW + "Canceled. " + event.note());
            case FINISHED -> {
                if (event.results().isEmpty()) player.sendMessage(ChatColor.GRAY + "No player completed the tournament.");
                for (var row : StrokeStandings.rank(event.results())) {
                    int reward = event.definition().prize(row.place());
                    player.sendMessage(ChatColor.GRAY + row.line() + (reward > 0 ? " • " + reward + " prize credits" : ""));
                }
                Set<UUID> scored = event.results().stream().map(StrokeStandings.Entry::player).collect(Collectors.toSet());
                event.entrants().entrySet().stream().sorted(java.util.Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                        .filter(entrant -> !scored.contains(entrant.getKey()))
                        .forEach(entrant -> player.sendMessage(ChatColor.GRAY + entrant.getValue() + " - "
                                + (event.starters().contains(entrant.getKey()) ? "Did not finish" : "Did not start")));
                if (!event.prizesPaid()) player.sendMessage(ChatColor.GRAY + "Prize delivery is pending.");
            }
        }
    }

    private static void help(Player player) {
        player.sendMessage(ChatColor.GRAY + "/ndg tournament signup | withdraw | standings [YYYY-MM-DD]");
    }

    static List<String> complete(String[] args) {
        return args.length == 2 ? List.of("signup", "withdraw", "standings", "status") : List.of();
    }
}
