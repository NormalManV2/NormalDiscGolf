package normalmanv2.normalDiscGolf.impl.command;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.competition.RankedSeason;
import normalmanv2.normalDiscGolf.impl.competition.RankedSeasons;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

final class RankedSeasonCommand {
    private static final int PAGE_SIZE = 10;

    private RankedSeasonCommand() {}

    static void execute(Player player, String[] args) {
        var seasons = NDGManager.getInstance().getRankedSeasons();
        String action = args.length == 1 ? "leaderboard" : args[1].toLowerCase(Locale.ROOT);
        try {
            if (action.equals("leaderboard") && args.length <= 4) {
                var season = args.length < 3 || args[2].equalsIgnoreCase("current")
                        ? seasons.current() : seasons.season(positiveNumber(args[2]));
                if (season == null) {
                    player.sendMessage(ChatColor.YELLOW + "That ranked season is unavailable. See /ndg season history.");
                    return;
                }
                leaderboard(player, seasons, season, args.length == 4 ? positiveNumber(args[3]) : 1);
            } else if (action.equals("history") && args.length <= 3) {
                history(player, seasons.history(), args.length == 3 ? positiveNumber(args[2]) : 1);
            } else help(player);
        } catch (NumberFormatException invalid) {
            help(player);
        } catch (IOException failure) {
            NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger()
                    .log(Level.WARNING, "Ranked season command failed", failure);
            player.sendMessage(ChatColor.RED + "Ranked season data is currently unavailable.");
        }
    }

    private static void leaderboard(Player player, RankedSeasons seasons, RankedSeason season, int page) {
        boolean active = season.includes(seasons.now());
        player.sendMessage(ChatColor.GOLD + "Ranked Season " + season.number() + " leaderboard");
        player.sendMessage(ChatColor.GRAY + (active ? "Ends " : "Closed ") + season.ends() + " UTC.");
        player.sendMessage(ChatColor.GRAY + "Minimum rated rounds to place: " + season.minimumRounds() + ".");
        var standings = season.standings();
        if (standings.isEmpty()) player.sendMessage(ChatColor.GRAY + "No qualified players yet.");
        for (var row : page(standings, page, player)) {
            int prize = season.prize(row.place());
            player.sendMessage(ChatColor.GRAY + "" + row.place() + ". " + row.entry().name() + " - "
                    + row.entry().rating() + " rating (" + rounds(row.entry().rounds()) + ")"
                    + (prize > 0 ? " | " + prize + " prize credits" : ""));
        }
        var entry = season.entries().get(player.getUniqueId());
        if (entry != null) {
            var place = standings.stream().filter(row -> row.player().equals(player.getUniqueId())).findFirst();
            String progress = place.map(row -> "place " + row.place())
                    .orElseGet(() -> rounds(season.minimumRounds() - entry.rounds()) + " left to qualify");
            player.sendMessage(ChatColor.AQUA + "Your season rating: " + entry.rating() + " (" + rounds(entry.rounds())
                    + ", " + progress + ").");
        } else if (active) {
            player.sendMessage(ChatColor.AQUA + "Finish a rated FFA or Doubles round to earn your season rating.");
        }
        if (!active && !season.prizesPaid()) player.sendMessage(ChatColor.GRAY + "Prize delivery is pending.");
    }

    private static void history(Player player, List<RankedSeason> history, int page) {
        player.sendMessage(ChatColor.GOLD + "Ranked season history");
        if (history.isEmpty()) player.sendMessage(ChatColor.GRAY + "No ranked seasons have ended yet.");
        for (var season : page(history, page, player)) {
            player.sendMessage(ChatColor.GRAY + "Season " + season.number() + " closed " + season.ends()
                    + " | /ndg season leaderboard " + season.number());
        }
    }

    private static <T> List<T> page(List<T> rows, int page, Player player) {
        int pages = Math.max(1, Math.ceilDiv(rows.size(), PAGE_SIZE));
        if (page > pages) {
            player.sendMessage(ChatColor.YELLOW + "This list has " + pages + (pages == 1 ? " page." : " pages."));
            return List.of();
        }
        if (pages > 1) player.sendMessage(ChatColor.GRAY + "Page " + page + " of " + pages);
        int start = (page - 1) * PAGE_SIZE;
        return rows.subList(start, start + Math.min(PAGE_SIZE, rows.size() - start));
    }

    private static String rounds(int rounds) { return rounds + (rounds == 1 ? " rated round" : " rated rounds"); }

    private static int positiveNumber(String input) {
        int number = Integer.parseInt(input);
        if (number < 1) throw new NumberFormatException("Positive season and page numbers required");
        return number;
    }

    private static void help(Player player) {
        player.sendMessage(ChatColor.GRAY + "/ndg season leaderboard [current|season number] [page]");
        player.sendMessage(ChatColor.GRAY + "/ndg season history [page]");
    }

    static List<String> complete(String[] args) {
        if (args.length == 2) return List.of("leaderboard", "history");
        return args.length == 3 && args[1].equalsIgnoreCase("leaderboard") ? List.of("current") : List.of();
    }
}
