package normalmanv2.normalDiscGolf.impl.competition;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.round.settings.RoundType;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.manager.round.lifecycle.RoundHandler;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.round.WeeklyTournamentRound;
import normalmanv2.normalDiscGolf.impl.team.TeamImpl;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

/** Runs on the server thread. The archive is written before crediting any placement prizes. */
public final class WeeklyTournaments {
    private final NormalDiscGolfPlugin plugin;
    private final PlayerDataManager profiles;
    private final RoundHandler rounds;
    private final RoundQueueManager queue;
    private final WeeklyTournamentStore store;
    private final Clock clock;
    private TreeMap<Instant, WeeklyTournament> events;
    private WeeklyTournamentRound active;
    private WeeklyTournament pendingResult;
    private Instant retryAt = Instant.MIN;

    public WeeklyTournaments(NormalDiscGolfPlugin plugin, PlayerDataManager profiles, RoundHandler rounds,
                             RoundQueueManager queue, Clock clock) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.rounds = rounds;
        this.queue = queue;
        this.clock = clock;
        this.store = new WeeklyTournamentStore(plugin.getDataFolder().toPath().resolve("weekly-tournaments.yml"));
    }

    public Instant now() { return clock.instant(); }
    public boolean enabled() { return plugin.getConfig().getBoolean("weekly-tournament.enabled", true); }

    public WeeklyTournament upcoming() throws IOException {
        ensureLoaded();
        if (!enabled()) return null;
        var next = events.values().stream().filter(event -> event.status() == WeeklyTournament.Status.SCHEDULED).findFirst().orElse(null);
        if (next != null) return next;
        Instant first = now();
        if (!events.isEmpty() && !events.lastKey().isBefore(first)) first = events.lastKey().plusSeconds(1);
        try {
            return commit(WeeklyTournament.scheduled(WeeklyTournament.next(plugin.getConfig(), first)));
        } catch (IllegalArgumentException | java.time.DateTimeException failure) {
            throw new IOException("Invalid weekly-tournament schedule", failure);
        }
    }

    public WeeklyTournament standings(LocalDate date) throws IOException {
        ensureLoaded();
        return events.descendingMap().values().stream()
                .filter(event -> date != null ? date.equals(event.definition().date()) : event.status() != WeeklyTournament.Status.SCHEDULED)
                .findFirst().orElse(null);
    }

    public String signUp(Player player, boolean joining) throws IOException {
        WeeklyTournament next = upcoming();
        if (next == null || !next.registrationOpen(now())) return "Weekly tournament registration is closed.";
        UUID id = player.getUniqueId();
        if (joining) {
            if (next.entrants().containsKey(id)) return "You are already signed up for this tournament.";
            if (next.entrants().size() >= next.definition().capacity()) return "This tournament is full.";
        } else if (!next.entrants().containsKey(id)) return "You are not signed up for this tournament.";
        commit(next.withEntrant(id, joining ? player.getName() : null));
        return joining ? "Signed up for the weekly tournament at " + next.definition().start() + ". Be online and free at the start."
                : "Withdrew from the weekly tournament.";
    }

    public void tick() {
        Instant now = now();
        if (now.isBefore(retryAt)) return;
        try {
            ensureLoaded();
            flushResult();
            for (var event : new ArrayList<>(events.values()))
                if (event.status() == WeeklyTournament.Status.FINISHED && !event.prizesPaid()) payPrizes(event);
            if (active != null) {
                if (!active.isOver()) return;
                var event = events.get(active.event().start());
                if (event.status() == WeeklyTournament.Status.RUNNING)
                    cancel(event, "The tournament round was interrupted.");
                active = null;
            }
            var next = upcoming();
            if (next != null && !now.isBefore(next.definition().start())) launch(next, now);
        } catch (IOException | RuntimeException failure) {
            retryAt = now.plusSeconds(15);
            plugin.getLogger().log(Level.WARNING, "Could not update weekly tournaments", failure);
        }
    }

    private void launch(WeeklyTournament scheduled, Instant now) throws IOException {
        if (now.isAfter(scheduled.definition().start().plus(WeeklyTournament.START_GRACE))) {
            cancel(scheduled, "The server missed the scheduled start.");
            return;
        }
        var admitted = new LinkedHashSet<UUID>();
        for (UUID id : scheduled.entrants().keySet()) if (queue.readyForScheduledRound(id)) admitted.add(id);
        if (admitted.size() < scheduled.definition().minimum()) {
            cancel(scheduled, "Too few registered players were ready at the start.");
            return;
        }
        var started = commit(scheduled.start(admitted));
        String id = "ndg_weekly_" + UUID.randomUUID();
        try {
            var course = new WeeklyCourse(started.definition(), id, plugin.getConfig());
            var settings = new DefaultRoundSettings(started.definition().capacity(), RoundType.RECREATIONAL, Division.JUNIOR, true);
            active = (WeeklyTournamentRound) rounds.createRound("weekly", course, id, settings);
            for (UUID playerId : admitted) {
                if (!active.addTeam(new TeamImpl(playerId, 1))) throw new IllegalStateException("Could not add tournament entrant");
            }
            for (UUID entrant : started.entrants().keySet()) {
                Player player = Bukkit.getPlayer(entrant);
                if (player == null) continue;
                if (admitted.contains(entrant)) player.sendMessage(ChatColor.GOLD + "The weekly tournament is preparing.");
                else player.sendMessage(ChatColor.YELLOW + "You were not ready at the weekly tournament start.");
            }
            rounds.startRound(active);
        } catch (RuntimeException failure) {
            if (active != null) {
                if (rounds.getActiveRounds().contains(active) || rounds.getPreparingRounds().contains(active)) rounds.cancelRound(active);
                else rounds.discardRound(active);
            }
            active = null;
            cancel(started, "The course could not start.");
            throw failure;
        }
    }

    public void finish(WeeklyTournamentRound round) {
        var event = events.get(round.event().start());
        if (event.status() != WeeklyTournament.Status.RUNNING || pendingResult != null) return;
        var scores = new ArrayList<StrokeStandings.Entry>();
        for (UUID id : event.starters()) {
            var card = round.finalCard(id);
            if (round.eligible(card, id))
                scores.add(new StrokeStandings.Entry(id, event.entrants().get(id), card.getTotalStrokes(), card.getTotalScore()));
        }
        pendingResult = event.finish(scores);
        try {
            flushResult();
            payPrizes(events.get(round.event().start()));
        } catch (IOException | RuntimeException failure) {
            retryAt = now().plusSeconds(15);
            plugin.getLogger().log(Level.WARNING, "Could not save tournament results or prizes. The server will retry.", failure);
        }
    }

    private void flushResult() throws IOException {
        if (pendingResult == null) return;
        var finished = commit(pendingResult);
        pendingResult = null;
        for (UUID entrant : finished.entrants().keySet()) {
            Player player = Bukkit.getPlayer(entrant);
            if (player != null) player.sendMessage(ChatColor.GOLD + "Weekly tournament final standings are available at /ndg tournament standings.");
        }
    }

    private void payPrizes(WeeklyTournament event) throws IOException {
        if (event.prizesPaid()) return;
        boolean allSaved = true;
        for (var standing : StrokeStandings.rank(event.results())) {
            int credits = event.definition().prize(standing.place());
            if (credits == 0) continue;
            UUID id = standing.entry().player();
            var result = profiles.creditCompetitionPrize(id, event.definition().id(), credits);
            if (result == PlayerDataManager.PrizeResult.SAVE_FAILED) allSaved = false;
            if (result == PlayerDataManager.PrizeResult.PAID) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) player.sendMessage(ChatColor.GOLD + "Weekly tournament reward: +" + credits + " credits for place " + standing.place() + ".");
            }
        }
        if (!allSaved) throw new IOException("One or more prize profiles could not be saved");
        commit(event.markPrizesPaid());
    }

    private void cancel(WeeklyTournament event, String reason) throws IOException {
        commit(event.cancel(reason));
        for (UUID entrant : event.entrants().keySet()) {
            Player player = Bukkit.getPlayer(entrant);
            if (player != null) player.sendMessage(ChatColor.YELLOW + "Weekly tournament canceled. " + reason);
        }
    }

    private void ensureLoaded() throws IOException {
        if (events != null) return;
        var saved = store.load();
        var recovered = new TreeMap<>(saved);
        recovered.replaceAll((date, event) -> event.status() == WeeklyTournament.Status.RUNNING
                ? event.cancel("The server stopped before the round finished.") : event);
        if (!recovered.equals(saved)) store.save(recovered);
        events = recovered;
    }

    private WeeklyTournament commit(WeeklyTournament event) throws IOException {
        var updated = new TreeMap<>(events);
        updated.put(event.definition().start(), event);
        store.save(updated);
        events = updated;
        return event;
    }
}
