package normalmanv2.normalDiscGolf.impl.competition;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.logging.Level;

/** All submissions, rollovers and payouts run on the server thread. */
public final class RankedSeasons {
    private record Submission(Instant completedAt, List<RankedSeason.Score> scores) {}

    private final NormalDiscGolfPlugin plugin;
    private final PlayerDataManager profiles;
    private final Clock clock;
    private final RankedSeasonStore store;
    private final ArrayDeque<Submission> pending = new ArrayDeque<>();
    private NavigableMap<Integer, RankedSeason> seasons;
    private Instant retryAt = Instant.MIN;

    public RankedSeasons(NormalDiscGolfPlugin plugin, PlayerDataManager profiles, Clock clock) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.clock = clock;
        this.store = new RankedSeasonStore(plugin.getDataFolder().toPath().resolve("ranked-seasons.yml"));
    }

    public Instant now() { return clock.instant(); }

    public boolean enabled() { return plugin.getConfig().getBoolean("ranked-seasons.enabled", true); }

    public RankedSeason current() throws IOException {
        if (!enabled()) return null;
        flushSubmissions();
        return advance(now());
    }

    public RankedSeason season(int number) throws IOException {
        current();
        ensureLoaded();
        return seasons.get(number);
    }

    public List<RankedSeason> history() throws IOException {
        current();
        ensureLoaded();
        Instant date = now();
        return seasons.descendingMap().values().stream().filter(season -> !date.isBefore(season.ends())).toList();
    }

    public void recordRound(Instant completedAt, List<RankedSeason.Score> scores) {
        if (!enabled() || scores.isEmpty()) return;
        pending.add(new Submission(completedAt, List.copyOf(scores)));
        try {
            flushSubmissions();
        } catch (IOException | RuntimeException failure) {
            retryAt = now().plusSeconds(15);
            plugin.getLogger().log(Level.WARNING, "Could not save season ratings. The server will retry.", failure);
            for (var score : scores) {
                var player = Bukkit.getPlayer(score.player());
                if (player != null) player.sendMessage(ChatColor.YELLOW + "Saving your season rating is pending.");
            }
        }
    }

    public void tick() {
        Instant date = now();
        if (date.isBefore(retryAt)) return;
        try {
            ensureLoaded();
            flushSubmissions();
            if (enabled()) advance(date);
            var expired = seasons.values().stream()
                    .filter(season -> !date.isBefore(season.ends()) && !season.prizesPaid()).toList();
            for (var season : expired) payPrizes(season);
        } catch (IOException | RuntimeException failure) {
            retryAt = date.plusSeconds(15);
            plugin.getLogger().log(Level.WARNING, "Could not update ranked seasons", failure);
        }
    }

    public void shutdown() {
        if (pending.isEmpty()) return;
        try {
            flushSubmissions();
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Could not save pending season ratings before shutdown", failure);
        }
    }

    private void flushSubmissions() throws IOException {
        while (!pending.isEmpty()) {
            var submission = pending.getFirst();
            var season = advance(submission.completedAt());
            var updated = commit(season.record(submission.scores()));
            pending.removeFirst();
            for (var score : submission.scores()) {
                var player = Bukkit.getPlayer(score.player());
                if (player == null) continue;
                var entry = updated.entries().get(score.player());
                player.sendMessage(ChatColor.AQUA + "Season " + updated.number() + " rating: " + entry.rating()
                        + " (" + entry.rounds() + (entry.rounds() == 1 ? " rated round)" : " rated rounds)"));
                if (entry.rounds() < updated.minimumRounds())
                    player.sendMessage(ChatColor.GRAY + "Complete " + updated.minimumRounds() + " rated rounds this season to place.");
            }
        }
    }

    private RankedSeason advance(Instant date) throws IOException {
        ensureLoaded();
        var config = plugin.getConfig();
        try {
            if (seasons.isEmpty())
                return commit(RankedSeason.open(1, date.truncatedTo(ChronoUnit.DAYS), config));
            var latest = seasons.lastEntry().getValue();
            if (latest.includes(date) || date.isBefore(latest.starts())) return latest;
            var opened = commit(latest.followAt(date, config));
            Bukkit.broadcastMessage(ChatColor.GOLD + "Ranked Season " + opened.number() + " is live. See /ndg season.");
            return opened;
        } catch (IllegalArgumentException | java.time.DateTimeException | ArithmeticException invalid) {
            throw new IOException("Invalid ranked season settings", invalid);
        }
    }

    private void payPrizes(RankedSeason season) throws IOException {
        boolean allSaved = true;
        for (var standing : season.standings()) {
            int credits = season.prize(standing.place());
            if (credits == 0) continue;
            var result = profiles.creditCompetitionPrize(standing.player(), "season-" + season.number(), credits);
            if (result == PlayerDataManager.PrizeResult.SAVE_FAILED) allSaved = false;
            if (result == PlayerDataManager.PrizeResult.PAID) {
                var player = Bukkit.getPlayer(standing.player());
                if (player != null) player.sendMessage(ChatColor.GOLD + "Season " + season.number() + " reward: +" + credits
                        + " credits for place " + standing.place() + ".");
            }
        }
        if (!allSaved) throw new IOException("One or more ranked season prizes could not be saved");
        commit(season.markPrizesPaid());
    }

    private void ensureLoaded() throws IOException {
        if (seasons == null) seasons = store.load();
    }

    private RankedSeason commit(RankedSeason season) throws IOException {
        var updated = new TreeMap<>(seasons);
        updated.put(season.number(), season);
        store.save(updated);
        seasons = updated;
        return season;
    }
}
