package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.impl.course.library.SavedCourse;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.lifecycle.RoundLifecycle;
import normalmanv2.normalDiscGolf.api.round.lifecycle.RoundResult;
import normalmanv2.normalDiscGolf.api.round.delegate.lifecycle.DelegateRoundLifecycle;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.competition.RankedSeason;
import normalmanv2.normalDiscGolf.impl.competition.StrokeStandings;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.clubhouse.ClubhouseMedal;
import normalmanv2.normalDiscGolf.impl.player.clubhouse.DailyChallenge;
import normalmanv2.normalDiscGolf.impl.player.score.PDGARating;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.player.score.RoundSummary;
import normalmanv2.normalDiscGolf.impl.round.RoundPresentation;
import normalmanv2.normalDiscGolf.impl.round.MatchPlayRound;
import normalmanv2.normalDiscGolf.impl.round.EliminationRound;
import normalmanv2.normalDiscGolf.impl.round.AceRaceRound;
import normalmanv2.normalDiscGolf.impl.round.ClosestToPinRound;
import normalmanv2.normalDiscGolf.impl.round.SingleThrowRound;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import normalmanv2.normalDiscGolf.impl.round.ArcadeRound;
import normalmanv2.normalDiscGolf.impl.round.DailyRound;
import normalmanv2.normalDiscGolf.impl.round.CompetitionRound;
import normalmanv2.normalDiscGolf.impl.round.WeeklyTournamentRound;
import normalmanv2.normalDiscGolf.impl.round.RoundFormat;
import normalmanv2.normalDiscGolf.impl.round.RoundState;
import normalmanv2.normalDiscGolf.impl.round.SetupTestRound;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.logging.Level;

public class DefaultRoundLifecycle implements DelegateRoundLifecycle {
    private boolean started = false;
    private boolean ended = false;
    private RoundState state;

    private GameRound round;
    private Plugin plugin;
    private boolean isWired = false;

    @Override
    public RoundLifecycle getRoundLifecycle() {
        return this;
    }

    @Override
    public void wire(@NotNull GameRound round, @NotNull Plugin plugin) {

        if (this.isWired) {
            throw new IllegalStateException("This lifecycle is already wired!");
        }

        this.round = round;
        this.plugin = plugin;
        this.isWired = true;
    }

    @Override
    public void unwire() {
        if (!this.isWired) {
            throw new IllegalStateException("This lifecycle is not wired!");
        }

        this.round = null;
        this.plugin = null;
        this.isWired = false;
    }

    @Override
    public boolean isWired() {
        return this.isWired;
    }

    @Override
    public RoundResult start() {
        if (this.started) return RoundResult.ALREADY_STARTED;
        if (this.round.getTeams().isEmpty()) return RoundResult.NOT_ENOUGH_TEAMS;
        if (this.round instanceof WeeklyTournamentRound weekly
                && this.round.getTeams().stream().filter(team -> Bukkit.getPlayer(team.getOwner()) != null).count() < weekly.event().minimum())
            return RoundResult.NOT_ENOUGH_TEAMS;
        if (this.round instanceof MatchPlayRound match && !match.hasOpponents()) return RoundResult.NOT_ENOUGH_TEAMS;
        if (this.round instanceof EliminationRound elimination && !elimination.hasOpponents()) return RoundResult.NOT_ENOUGH_TEAMS;

        this.started = true;
        this.state = RoundState.START;

        World world = ((RoundImpl) this.round).world();
        if (world == null) {
            throw new IllegalStateException("World " + this.round.getId() + " does not exist");
        }

        NDGManager.getInstance().getPlayerSessionManager().beginRound(this.round);

        for (Team team : this.round.getTeams()) {
            this.round.getRoundScoreCardManager().addScoreCard(team, new ScoreCard());
            Set<UUID> missingPlayers = new HashSet<>();

            for (UUID playerId : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    missingPlayers.add(playerId);
                    continue;
                }

                NDGManager.getInstance().getClubhouse().leavePractice(playerId);

                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
                player.setAllowFlight(false);
                player.setInvulnerable(true);
                player.setCollidable(false);
                player.setFoodLevel(20);
                player.setFireTicks(0);
                player.closeInventory();
                normalmanv2.normalDiscGolf.impl.listener.RoundPlayerListener.giveKit(player,
                        (normalmanv2.normalDiscGolf.impl.round.RoundImpl) this.round);
            }

            missingPlayers.forEach(team::removePlayer);
            if (team.getTeamMembers().isEmpty()) {
                this.round.getRoundTeamManager().removeTeam(team);
                this.round.getRoundScoreCardManager().removeScoreCard(team);
            }
        }

        if (this.round instanceof MatchPlayRound match) match.openMatch();
        if (this.round instanceof EliminationRound elimination) elimination.open();
        if (this.round instanceof ArcadeRound arcade)
            sendRoundMessage(ChatColor.LIGHT_PURPLE + "Arcade adds Phantom, Rocket, and Sticky discs with "
                    + arcade.chargeLimit() + " charge(s) each for this round. Check /ndg status for charges.");
        if (this.round instanceof AceRaceRound)
            sendRoundMessage(ChatColor.GOLD + "Ace Race: One throw per hole. Most aces wins, and equal totals share a place.");
        if (this.round instanceof ClosestToPinRound) {
            sendRoundMessage(ChatColor.AQUA + "Closest to Pin gives you one throw per hole.");
            sendRoundMessage(ChatColor.GRAY + "Fewest faults wins. Among equal fault counts, the lowest total distance wins.");
        }
        if (RoundFormat.of(this.round) == RoundFormat.BEST_SHOT) {
            sendRoundMessage(ChatColor.GREEN + "Best Shot Doubles. Both teammates throw from the same lie.");
            sendRoundMessage(ChatColor.GRAY + "Click a lie after both throws, or use /ndg lie 1 or /ndg lie 2.");
        }
        if (RoundFormat.of(this.round) == RoundFormat.WORST_SHOT) {
            sendRoundMessage(ChatColor.GOLD + "Worst Shot Doubles. Both teammates throw from the same lie.");
            sendRoundMessage(ChatColor.GRAY + "Play the worse result. Both players must hole out to finish the stroke in the basket.");
        }
        this.round.getRoundTurnManager().selectRandomTeamTurn();
        if (this.round instanceof DailyRound daily) {
            sendRoundMessage(ChatColor.AQUA + daily.definition().description());
            sendRoundMessage(ChatColor.GRAY + "Everyone uses starter discs and the same skill bonuses. Hole out on every hole to post a daily score.");
            try {
                var ghosts = daily.play().enableGhosts("Daily", "/ndg daily ghost");
                var competition = NDGManager.getInstance().getDailyCompetition();
                var board = competition.board(daily.definition().date());
                if (board != null && board.course().equals(daily.definition()))
                    ghosts.loadBaselines(board.entries(), entry -> competition.ghost(board, entry.player()));
            } catch (IOException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not load Daily ghost baselines", failure);
            }
        } else if (this.round instanceof WeeklyTournamentRound weekly) {
            sendRoundMessage(ChatColor.GOLD + "Weekly tournament is live. " + weekly.event().date() + " UTC.");
            sendRoundMessage(ChatColor.GRAY + "Everyone uses starter discs and the same skill bonuses. Hole out on every hole to place.");
        } else if (this.round.getCourse() instanceof normalmanv2.normalDiscGolf.impl.course.CourseImpl course)
            sendRoundMessage(ChatColor.AQUA + course.generationInfo());
        if (round instanceof SetupTestRound)
            sendRoundMessage(ChatColor.AQUA + "Setup test round • One hole • Practice only");
        if (this.round instanceof RoundImpl live && live.getCourse().isLibraryReplay()
                && RoundFormat.of(live) == RoundFormat.FFA) {
            var ghosts = live.play().enableGhosts("Course", "/ndg library ghost");
            var records = NDGManager.getInstance().getCourseRecords();
            var course = live.getCourse().libraryCourse();
            try {
                ghosts.loadBaselines(records.scores(course), entry -> records.ghost(course, entry));
            } catch (IOException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not load saved-layout records", failure);
            }
        }
        if (this.round instanceof RoundImpl liveRound && !(liveRound instanceof CompetitionRound) && liveRound.play().normalizesCareerSkills())
            sendRoundMessage(ChatColor.GRAY + "Career XP is active. Every player uses the same skill bonuses in this rated format.");
        ((normalmanv2.normalDiscGolf.impl.round.RoundImpl)this.round).play().startHole();
        for (Team team : this.round.getTeams()) for (UUID id : team.getTeamMembers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) RoundPresentation.teleportToCurrentTee(this.plugin, player, this.round, true);
        }
        this.round.setGameTask(Bukkit.getScheduler().runTaskTimer(this.plugin,
                () -> ((normalmanv2.normalDiscGolf.impl.round.RoundImpl)this.round).play().tick(), 20, 20));
        return RoundResult.SUCCESS;
    }

    @Override
    public RoundResult end() {
        if (!this.started || this.ended) {
            return RoundResult.INVALID;
        }
        this.ended = true;
        this.state = RoundState.END;

        try {
            this.applyRoundRewards();
            this.sendRoundResults();
            this.sendRoundSummaries();
            if (round instanceof DailyRound daily) publishDailyResults(daily);
            if (round instanceof RoundImpl live && live.getCourse().isLibraryReplay() && live.play().ghosts() != null)
                publishCourseRecords(live);
            if (round instanceof WeeklyTournamentRound weekly) NDGManager.getInstance().getWeeklyTournaments().finish(weekly);
        } finally {
            this.restoreRoundPlayers();
            this.round.dispose();
        }
        return RoundResult.ENDED;
    }

    private void applyRoundRewards() {
        if (round instanceof WeeklyTournamentRound || round instanceof SetupTestRound) return;
        int creditsPerBasket = Math.max(0, plugin.getConfig().getInt("disc-shop.credits-per-basket", 10));
        if (round instanceof MatchPlayRound match) {
            payMatchBaskets(match, creditsPerBasket);
            return;
        }
        if (round instanceof EliminationRound elimination) {
            for (UUID id : elimination.entrants()) payBasketCredits(id, elimination.rewardBaskets(id), creditsPerBasket);
            return;
        }
        if (round instanceof SingleThrowRound || round instanceof ArcadeRound || round instanceof DailyRound
                || round instanceof RoundImpl impl && (impl.getCourse().isCustom() || impl.getCourse().isLibraryReplay())) {
            for (Team team : round.getTeams())
                payBasketCredits(team.getOwner(), round.getScoreCardByTeam(team).getHoledOutCount(), creditsPerBasket);
            return;
        }
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        RoundFormat format = RoundFormat.of(round);
        if (format == null) format = RoundFormat.FFA;
        int holes = round.getCourse().holes();
        var seasons = NDGManager.getInstance().getRankedSeasons();
        var completedAt = seasons.now();
        var seasonScores = new ArrayList<RankedSeason.Score>();
        for (Team team : this.round.getTeams()) {
            ScoreCard scoreCard = this.round.getScoreCardByTeam(team);
            if (scoreCard == null || scoreCard.getHoleScores().size() != this.round.getCourse().holes()) {
                continue;
            }

            for (UUID playerId : team.getTeamMembers()) {
                PlayerData playerData = this.round.getPlayerDataManager().getDataByPlayer(playerId);
                if (playerData == null) {
                    continue;
                }

                Player player = Bukkit.getPlayer(playerId);
                if (format.countsForStrokeRating()) {
                    var change = applyCareerRating(player, playerData.getRating(), playerId, scoreCard, holes);
                    if (change != null)
                        seasonScores.add(new RankedSeason.Score(playerId, formatPlayerName(playerId), change.roundRating(), holes));
                }
                var clubhouse = playerData.getClubhouse();
                var result = clubhouse.recordRound(scoreCard, playerId, format.id(), holes, day);
                playerData.setClubhouse(result.progress());
                long credits = (long) scoreCard.getHoledOutCount() * creditsPerBasket + result.bonusCredits();
                playerData.setDiscBag(playerData.getDiscBag().earn(credits));
                this.round.getPlayerDataManager().savePlayerData(playerId);
                if (player != null && credits > 0) {
                    player.sendMessage(ChatColor.GOLD + "Round reward: +" + credits + " credits. Balance: "
                            + playerData.getDiscBag().credits() + ". Open /ndg shop.");
                    if (result.streakCredits() > 0)
                        player.sendMessage(ChatColor.GOLD + "Basket streak bonus: +" + result.streakCredits() + " credits.");
                    if (result.dailyCredits() > 0)
                        player.sendMessage(ChatColor.GREEN + "Daily challenge complete: " + DailyChallenge.forDay(day).title()
                                + " (+" + result.dailyCredits() + " credits).");
                    for (ClubhouseMedal medal : result.newMedals())
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "Clubhouse medal: " + medal.title() + " (+" + medal.credits() + " credits).");
                }
                if (player != null && result.bestCard()) {
                    player.sendMessage(ChatColor.AQUA + "New personal best: " + formatRelativeScore(scoreCard.getTotalScore())
                            + " on a " + holes + "-hole " + format.label() + " card.");
                }
            }
        }
        seasons.recordRound(completedAt, seasonScores);
    }

    private void publishDailyResults(DailyRound daily) {
        var attempts = new ArrayList<StrokeStandings.Entry>();
        for (Team team : daily.getTeams()) {
            UUID id = team.getOwner();
            ScoreCard card = daily.getScoreCardByTeam(team);
            Player player = Bukkit.getPlayer(id);
            if (player != null) daily.play().ghosts().sendComparison(player, false);
            if (daily.eligible(card, id)) {
                attempts.add(new StrokeStandings.Entry(id, formatPlayerName(id), card.getTotalStrokes(), card.getTotalScore()));
            } else {
                if (player != null) player.sendMessage(ChatColor.YELLOW + "Hole out on every hole to post to the Daily Course leaderboard.");
            }
        }
        if (attempts.isEmpty()) return;
        try {
            var competition = NDGManager.getInstance().getDailyCompetition();
            var updated = competition.submit(daily.definition(), attempts);
            NDGManager.getInstance().getClubhouse().refreshBoard();
            Set<UUID> finished = new HashSet<>();
            for (var attempt : attempts) {
                UUID id = attempt.player();
                finished.add(id);
                if (updated.entries().get(id).strokes() != attempt.strokes()) continue;
                var ghost = daily.play().ghosts().completed(id, daily.getScoreCardById(id));
                if (ghost == null) continue;
                Player player = Bukkit.getPlayer(id);
                try {
                    if (competition.saveGhost(updated, attempt, ghost) && player != null)
                        player.sendMessage(ChatColor.AQUA + "Daily ghost saved. Use /ndg daily ghost on in your next attempt.");
                } catch (java.io.IOException failure) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not save Daily ghost for " + id, failure);
                    if (player != null) player.sendMessage(ChatColor.YELLOW + "Your score was saved. The Daily ghost could not be saved.");
                }
            }
            for (var standing : updated.standings()) {
                UUID id = standing.entry().player();
                if (!finished.contains(id)) continue;
                Player player = Bukkit.getPlayer(id);
                if (player != null) player.sendMessage(ChatColor.AQUA + "Daily best: " + standing.entry().score()
                        + " • Place " + standing.place() + " • " + daily.definition().date() + " UTC");
            }
        } catch (java.io.IOException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not save daily scores", failure);
            sendRoundMessage(ChatColor.RED + "Your round finished. The Daily Course leaderboard could not save the scores.");
        }
    }

    private void publishCourseRecords(RoundImpl live) {
        var course = live.getCourse().libraryCourse();
        var ghosts = live.play().ghosts();
        var attempts = new ArrayList<StrokeStandings.Entry>();
        for (var team : live.getTeams()) {
            UUID id = team.getOwner();
            Player player = Bukkit.getPlayer(id);
            var card = live.getScoreCardByTeam(team);
            if (player != null) ghosts.sendComparison(player, false);
            if (card != null && card.completedBy(id, course.holes())) {
                attempts.add(new StrokeStandings.Entry(id, formatPlayerName(id), card.getTotalStrokes(), card.getTotalScore()));
            } else if (player != null) {
                player.sendMessage(ChatColor.YELLOW + "Hole out on every hole to post a course record.");
            }
        }
        if (attempts.isEmpty()) return;

        var records = NDGManager.getInstance().getCourseRecords();
        try {
            var updated = records.submit(course, attempts);
            for (var attempt : attempts) {
                UUID id = attempt.player();
                var best = updated.best().get(id);
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.sendMessage(ChatColor.AQUA + (updated.improved().contains(id) ? "New course best: " : "Your course best: ")
                            + best.score());
                }
                if (best.strokes() != attempt.strokes()) continue;
                var ghost = ghosts.completed(id, live.getScoreCardById(id));
                if (ghost == null) continue;
                try {
                    if (records.saveGhost(course, best, ghost) && player != null)
                        player.sendMessage(ChatColor.AQUA + "Course ghost saved. Use /ndg library ghost play in your next round.");
                } catch (IOException failure) {
                    plugin.getLogger().log(Level.WARNING, "Could not save course ghost for " + id, failure);
                    if (player != null)
                        player.sendMessage(ChatColor.YELLOW + "Your course best was saved. The ghost could not be saved.");
                }
            }
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not save layout records", failure);
            sendRoundMessage(ChatColor.RED + "Your round finished. The course records could not be saved.");
        }
    }

    private PDGARating.Change applyCareerRating(Player player, PDGARating rating, UUID id, ScoreCard card, int holes) {
        if (card.getHoledOutCount() != holes || !card.participated(id)) {
            if (player != null)
                player.sendMessage(ChatColor.GRAY + "To earn a rating, throw in the round and hole out on every hole.");
            return null;
        }
        int difficulty = round.getCourse() instanceof normalmanv2.normalDiscGolf.impl.course.CourseImpl course
                ? course.grid().difficultyRating() : 0;
        var change = rating.recordRound(card.getTotalScore(), holes, difficulty);
        if (player == null) return change;
        player.sendMessage(ChatColor.AQUA + "Round rating: " + change.roundRating() + " • Career rating: "
                + (change.before() == 0 ? "Unrated" : change.before()) + " → " + change.after());
        if (change.division() != change.previousDivision()) {
            boolean promoted = change.division().ordinal() > change.previousDivision().ordinal();
            player.sendMessage((promoted ? ChatColor.GOLD + "Promoted to " : ChatColor.YELLOW + "Division changed to ")
                    + change.division().label() + ".");
        }
        return change;
    }

    private void payMatchBaskets(MatchPlayRound match, int creditsPerBasket) {
        for (UUID id : List.of(match.score().first(), match.score().second())) {
            if (id.equals(match.score().forfeiter())) continue;
            ScoreCard card = match.getScoreCardById(id);
            if (card != null) payBasketCredits(id, card.getHoledOutCount(), creditsPerBasket);
        }
    }

    private void payBasketCredits(UUID id, int baskets, int creditsPerBasket) {
        long credits = (long) baskets * creditsPerBasket;
        if (credits == 0) return;
        var profiles = round.getPlayerDataManager();
        PlayerData data = profiles.getOrCreatePlayerData(id);
        data.setDiscBag(data.getDiscBag().earn(credits));
        profiles.savePlayerData(id);
        Player player = Bukkit.getPlayer(id);
        if (player != null) player.sendMessage(ChatColor.GOLD + "Round reward: +" + credits + " credits. Balance: "
                + data.getDiscBag().credits() + ". Open /ndg shop.");
    }

    @Override
    public boolean isOver() {
        return this.ended || this.state == RoundState.END || this.state == RoundState.CANCEL;
    }

    @Override
    public RoundState getState() {
        return this.state;
    }

    @Override
    public void setState(RoundState state) {
        this.state = state;
    }

    @Override
    public RoundResult cancel() {
        if (!this.started) return RoundResult.INVALID;
        this.ended = true;
        this.state = RoundState.CANCEL;
        try {
            this.sendRoundMessage(ChatColor.RED + "Round canceled.");
        } finally {
            this.restoreRoundPlayers();
            this.round.dispose();
        }
        return RoundResult.CANCELED;
    }

    private void restoreRoundPlayers() {
        RoundPresentation.cancelRound(this.round);
        if (this.round instanceof RoundImpl active) active.play().spectating().dispose();
        NDGManager.getInstance().getPlayerSessionManager().restoreRoundSessions(this.round);
    }

    private void sendRoundResults() {
        if (round instanceof ClosestToPinRound closest) {
            var standings = closest.standings();
            sendRoundMessage(ChatColor.GREEN + "Round complete!");
            sendRoundMessage(ChatColor.GOLD + closest.result(standings));
            sendRoundMessage(ChatColor.GRAY + "Final standings:");
            for (var entry : standings) sendRoundMessage(ChatColor.GRAY + entry.line());
            return;
        }
        if (round instanceof AceRaceRound race) {
            var standings = race.standings();
            sendRoundMessage(ChatColor.GREEN + "Round complete!");
            sendRoundMessage(ChatColor.GOLD + race.result(standings));
            sendRoundMessage(ChatColor.GRAY + "Final standings:");
            for (var entry : standings) sendRoundMessage(ChatColor.GRAY + entry.line());
            return;
        }
        if (round instanceof EliminationRound elimination) {
            var results = elimination.results();
            for (UUID id : elimination.entrants()) {
                Player player = Bukkit.getPlayer(id);
                if (player == null) continue;
                player.sendMessage(ChatColor.GREEN + "Round complete!");
                for (String result : results) player.sendMessage(ChatColor.GOLD + result);
            }
            return;
        }
        if (round instanceof MatchPlayRound match) {
            sendRoundMessage(ChatColor.GREEN + "Round complete!");
            sendRoundMessage(ChatColor.GOLD + match.resultLine());
            sendRoundMessage(ChatColor.GRAY + match.resultsLine());
            return;
        }
        List<Map.Entry<Team, ScoreCard>> standings = new ArrayList<>(this.round.getScoreCards().entrySet());
        standings.sort(Comparator
                .comparingInt((Map.Entry<Team, ScoreCard> entry) -> entry.getValue().getTotalScore())
                .thenComparingInt(entry -> entry.getValue().getTotalStrokes())
                .thenComparing(entry -> this.formatTeamMembers(entry.getKey())));

        for (Team team : this.round.getTeams()) {
            for (UUID playerId : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    continue;
                }

                player.sendMessage(ChatColor.GREEN + "Round complete!");
                player.sendMessage(ChatColor.GRAY + "Final standings:");

                for (int index = 0; index < standings.size(); index++) {
                    Map.Entry<Team, ScoreCard> entry = standings.get(index);
                    ScoreCard scoreCard = entry.getValue();
                    player.sendMessage(ChatColor.GRAY.toString()
                            + (index + 1)
                            + ". "
                            + this.formatTeamMembers(entry.getKey())
                            + " - "
                            + this.formatRelativeScore(scoreCard.getTotalScore())
                            + " ("
                            + scoreCard.getTotalStrokes()
                            + " strokes)");
                }
            }
        }
    }

    private void sendRoundSummaries() {
        if (!(round instanceof RoundImpl active)) return;
        RoundFormat format = RoundFormat.of(active);
        var course = active.getCourse();
        var savedCourse = round instanceof SetupTestRound ? null : SavedCourse.capture(course, format);
        String label = (round instanceof SetupTestRound ? "Setup Test" : format == null ? "Disc Golf" : format.label()) + " | "
                + (course.isCustom() ? course.name() : course.grid().terrain().label());
        Set<UUID> recipients = new HashSet<>();
        if (round instanceof EliminationRound elimination) recipients.addAll(elimination.entrants());
        else for (Team team : round.getTeams()) recipients.addAll(team.getTeamMembers());
        for (UUID id : recipients) {
            var summary = new RoundSummary(label, summaryScore(active, id), active.play().analytics(id),
                    !course.isCustom(), savedCourse);
            active.getPlayerDataManager().getOrCreatePlayerData(id).setLatestRoundSummary(summary);
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                summary.sendTo(player);
                var link = new net.md_5.bungee.api.chat.TextComponent("[Open Round Summary]");
                link.setColor(net.md_5.bungee.api.ChatColor.GOLD);
                link.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/ndg summary"));
                player.spigot().sendMessage(link);
            }
        }
    }

    private String summaryScore(RoundImpl active, UUID player) {
        if (active instanceof EliminationRound elimination)
            return "Score: place " + elimination.place(player) + " of " + elimination.entrants().size();
        Team team = active.play().team(player);
        if (team == null) return "Score: —";
        if (active instanceof ClosestToPinRound closest) return "Score: " + closest.total(team).label();
        if (active instanceof MatchPlayRound match)
            return "Score: " + match.score().wins(player) + " hole wins, " + match.score().halved() + " halved";
        ScoreCard card = active.getScoreCardByTeam(team);
        if (card == null) return "Score: —";
        if (active instanceof AceRaceRound) return "Score: " + AceRaceRound.aceCount(card.getHoledOutCount());
        return (team.getMaximumPlayers() > 1 ? "Team score: " : "Score: ") + formatRelativeScore(card.getTotalScore())
                + " (" + MatchPlayRound.strokeCount(card.getTotalStrokes()) + ", par " + (card.getTotalStrokes() - card.getTotalScore())
                + ") • " + card.getHoleScores().size() + "/" + active.getCourse().holes() + " holes";
    }

    private void sendRoundMessage(String message) {
        for (Team team : this.round.getTeams()) {
            for (UUID playerId : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(message);
                }
            }
        }
    }

    private String formatTeamMembers(Team team) {
        StringJoiner joiner = new StringJoiner(", ");
        for (UUID playerId : team.getTeamMembers()) {
            joiner.add(this.formatPlayerName(playerId));
        }
        return joiner.toString();
    }

    private String formatPlayerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }

        String playerName = Bukkit.getOfflinePlayer(playerId).getName();
        return playerName != null ? playerName : playerId.toString();
    }

    private String formatRelativeScore(int score) {
        if (score > 0) {
            return "+" + score;
        }
        return String.valueOf(score);
    }
}
