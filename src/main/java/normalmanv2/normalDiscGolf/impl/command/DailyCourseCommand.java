package normalmanv2.normalDiscGolf.impl.command;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.logging.Level;

final class DailyCourseCommand {
    private static final int PAGE_SIZE = 10;

    private DailyCourseCommand() {}

    static void leaderboard(Player player, String[] args) {
        if (args.length > 4 || args.length > 1 && !args[1].equalsIgnoreCase("leaderboard")) {
            player.sendMessage(ChatColor.YELLOW + "Use /ndg daily play or /ndg daily leaderboard [today|YYYY-MM-DD] [page].");
            return;
        }
        var daily = NDGManager.getInstance().getDailyCompetition();
        LocalDate date;
        int page;
        try {
            date = args.length < 3 || args[2].equalsIgnoreCase("today") ? daily.todayDate() : LocalDate.parse(args[2]);
            page = args.length < 4 ? 1 : Integer.parseInt(args[3]);
        } catch (DateTimeParseException | NumberFormatException invalid) {
            player.sendMessage(ChatColor.RED + "Use a date like 2026-11-26 and a whole-number page.");
            return;
        }
        try {
            var board = daily.board(date);
            if (board == null) {
                player.sendMessage(ChatColor.YELLOW + "There is no Daily Course leaderboard for " + date + " UTC.");
                return;
            }
            var standings = board.standings();
            int pages = Math.max(1, (standings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page < 1 || page > pages) {
                player.sendMessage(ChatColor.YELLOW + "Choose a leaderboard page from 1 to " + pages + ".");
                return;
            }
            player.sendMessage(ChatColor.AQUA + board.course().description());
            player.sendMessage(ChatColor.GOLD + "Daily leaderboard (" + page + "/" + pages + ")");
            var rows = standings.subList((page - 1) * PAGE_SIZE, Math.min(page * PAGE_SIZE, standings.size()));
            for (var row : rows) player.sendMessage(ChatColor.GRAY + row.line());
            if (standings.isEmpty()) player.sendMessage(ChatColor.GRAY + "No completed scores yet.");
            for (var row : standings) if (row.entry().player().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.GREEN + "Your daily best: " + row.entry().score() + " • Place " + row.place());
                break;
            }
            if (date.equals(daily.todayDate()))
                player.sendMessage(ChatColor.GRAY + "Use /ndg daily play to enter.");
        } catch (IOException failure) {
            NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger()
                    .log(Level.WARNING, "Could not read daily leaderboard for " + date, failure);
            player.sendMessage(ChatColor.RED + "The Daily Course leaderboard is currently unavailable.");
        }
    }

    static List<String> complete(String[] args) {
        if (args.length == 2) return List.of("play", "leaderboard");
        if (args.length == 3 && args[1].equalsIgnoreCase("leaderboard")) return List.of("today");
        return List.of();
    }
}
