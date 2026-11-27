package normalmanv2.normalDiscGolf.impl.command;

import normalmanv2.normalDiscGolf.impl.course.grid.CourseTerrain;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.command.AbstractCommand;
import normalmanv2.normalDiscGolf.common.invite.Invite;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.cosmetic.ScoreDecal;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.gui.DiscShop;
import normalmanv2.normalDiscGolf.impl.gui.DecalShop;
import normalmanv2.normalDiscGolf.impl.gui.BagMenu;
import normalmanv2.normalDiscGolf.impl.gui.ClubhouseMenu;
import normalmanv2.normalDiscGolf.impl.event.PlayerQueueRoundEvent;
import normalmanv2.normalDiscGolf.impl.gui.round.RoundPickerGUI;
import normalmanv2.normalDiscGolf.impl.manager.party.PartyManager;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager;
import normalmanv2.normalDiscGolf.impl.round.MatchPlayRound;
import normalmanv2.normalDiscGolf.impl.round.EliminationRound;
import normalmanv2.normalDiscGolf.impl.round.AceRaceRound;
import normalmanv2.normalDiscGolf.impl.round.ClosestToPinRound;
import normalmanv2.normalDiscGolf.impl.round.ArcadeRound;
import normalmanv2.normalDiscGolf.impl.round.DailyRound;
import normalmanv2.normalDiscGolf.impl.round.BestShotRound;
import normalmanv2.normalDiscGolf.impl.round.RoundFormat;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import normalmanv2.normalDiscGolf.impl.round.RoundPresentation;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.player.score.PDGARating;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.skill.Skill;
import normalmanv2.normalDiscGolf.impl.team.TeamImpl;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Collectors;

public class DiscGolfCommand extends AbstractCommand {

    private static final Duration PARTY_INVITE_DURATION = Duration.ofSeconds(60);

    public DiscGolfCommand(String command, String[] aliases, String description, String permission) {
        super(command, aliases, description, permission);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            this.sendHelp(sender);
            return;
        }

        if ((args[0].equalsIgnoreCase("profile") || args[0].equalsIgnoreCase("stats")) && args.length > 1) {
            ProfileAdminCommand.execute(sender, args);
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use disc golf gameplay commands.");
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "queue", "join" -> this.queuePlayer(player, args);
            case "leave", "dequeue" -> this.leaveQueue(player);
            case "menu", "play" -> this.openMenu(player);
            case "status" -> this.sendStatus(player);
            case "score", "scorecard" -> this.sendScore(player);
            case "summary" -> {
                var summary = NDGManager.getInstance().getPlayerDataManager().getOrCreatePlayerData(player.getUniqueId()).getLatestRoundSummary();
                if (summary == null) player.sendMessage(ChatColor.YELLOW + "Finish a round to see a round summary.");
                else summary.sendTo(player);
            }
            case "career", "profile", "stats" -> this.sendProfile(player);
            case "shop" -> this.shop(player, args);
            case "decals" -> this.decals(player, args);
            case "bag" -> BagMenu.open(player);
            case "clubhouse" -> ClubhouseMenu.open(player);
            case "course" -> CourseCommand.execute(player, args, this::formatQueueResult);
            case "daily" -> {
                if (args.length == 2 && args[1].equalsIgnoreCase("play")) this.queuePlayer(player, new String[]{"queue", "daily"});
                else DailyCourseCommand.leaderboard(player, args);
            }
            case "tournament" -> WeeklyTournamentCommand.execute(player, args);
            case "season" -> RankedSeasonCommand.execute(player, args);
            case "shot" -> {
                if (findActiveRound(player.getUniqueId()) instanceof normalmanv2.normalDiscGolf.impl.round.RoundImpl round)
                    ShotCommand.execute(player, round, args);
                else player.sendMessage(ChatColor.RED + "Shot settings are available during a round.");
            }
            case "pack" -> this.resourcePack(player, args);
            case "party" -> this.handleParty(player, args);
            case "cancel" -> this.cancelRound(player);
            case "camera", "replay" -> this.spectating(player, args);
            case "skip" -> {
                RoundPresentation.cancel(player);
                if (findActiveRound(player.getUniqueId()) instanceof RoundImpl round)
                    round.play().spectating().skip(player.getUniqueId());
            }
            case "lie" -> {
                GameRound active = this.findActiveRound(player.getUniqueId());
                if (active instanceof normalmanv2.normalDiscGolf.impl.round.RoundImpl impl) {
                    if (args.length > 1) {
                        int choice = switch (args[1]) {
                            case "1" -> 1;
                            case "2" -> 2;
                            default -> 0;
                        };
                        if (args.length > 3) player.sendMessage(ChatColor.RED + "Use /ndg lie 1 or /ndg lie 2.");
                        else impl.play().chooseLie(player, choice, args.length == 3 ? args[2] : null);
                        break;
                    }
                    if (impl.play().showLieOptions(player)) break;
                    if (impl.play().inFlight()) { player.sendMessage("§cWait for the disc to land."); break; }
                    if (impl.play().spectating().replaying()) {
                        player.sendMessage(ChatColor.RED + "Wait for the instant replay to finish.");
                        break;
                    }
                    RoundPresentation.cancel(player);
                    var lie = impl.play().lie(impl.play().team(player.getUniqueId()));
                    if (lie != null) player.teleport(lie);
                }
            }
            case "concede" -> {
                GameRound active = this.findActiveRound(player.getUniqueId());
                if (active instanceof normalmanv2.normalDiscGolf.impl.round.RoundImpl impl) impl.play().concede(player);
            }
            case "quit" -> NDGManager.getInstance().getRoundHandler().leavePlayer(player);
            default -> this.sendHelp(player);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length >= 2 && (args[0].equalsIgnoreCase("profile") || args[0].equalsIgnoreCase("stats"))) {
            return ProfileAdminCommand.complete(sender, args);
        }
        if (args.length == 1) {
            return this.matching(args[0], List.of("queue", "leave", "menu", "status", "score", "summary", "career", "profile", "shop", "bag", "clubhouse", "course", "daily", "tournament", "season", "decals", "pack", "party", "shot", "camera", "replay", "cancel", "skip", "lie", "concede", "quit", "help"));
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("shot"))
            return matching(args[args.length - 1], ShotCommand.complete(args));
        if (args.length >= 2 && args[0].equalsIgnoreCase("course") && sender instanceof Player player)
            return matching(args[args.length - 1], CourseCommand.complete(player, args));
        if (args.length >= 2 && args[0].equalsIgnoreCase("daily"))
            return matching(args[args.length - 1], DailyCourseCommand.complete(args));
        if (args.length >= 2 && args[0].equalsIgnoreCase("tournament"))
            return matching(args[args.length - 1], WeeklyTournamentCommand.complete(args));
        if (args.length >= 2 && args[0].equalsIgnoreCase("season"))
            return matching(args[args.length - 1], RankedSeasonCommand.complete(args));
        if (args.length == 2 && (args[0].equalsIgnoreCase("camera") || args[0].equalsIgnoreCase("replay")))
            return matching(args[1], List.of("on", "off"));

        if (args[0].equalsIgnoreCase("shop")) {
            if (args.length == 2) return matching(args[1], List.of("buy", "pack"));
            if (args.length == 3) return matching(args[2],
                    NDGManager.getInstance().getDiscCatalog().models().stream().map(DiscModel::id).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pack")) return matching(args[1], List.of("retry"));
        if (args[0].equalsIgnoreCase("decals")) {
            if (args.length == 2) return matching(args[1], List.of("buy", "equip", "preview"));
            if (args.length == 3) return matching(args[2], Arrays.stream(ScoreDecal.values()).map(ScoreDecal::id).toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("queue")) {
            return this.matching(args[1], Arrays.stream(RoundFormat.values()).filter(RoundFormat::canQueueDirectly).map(RoundFormat::id).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lie") && sender instanceof Player player
                && findActiveRound(player.getUniqueId()) instanceof BestShotRound best
                && best.play().choosingLie() != null && best.play().choosingLie().contains(player.getUniqueId()))
            return matching(args[1], List.of("1", "2"));

        if (args.length == 2 && args[0].equalsIgnoreCase("party")) {
            return this.matching(args[1], List.of("invite", "accept", "decline", "leave", "status"));
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("party") && args[1].equalsIgnoreCase("invite")) {
            return Bukkit.getOnlinePlayers()
                    .stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }

        return List.of();
    }

    private void shop(Player player, String[] args) {
        if (args.length == 1) {
            DiscShop.open(player);
            return;
        }
        boolean buy = args[1].equalsIgnoreCase("buy");
        if (args.length != 3 || (!buy && !args[1].equalsIgnoreCase("pack") && !args[1].equalsIgnoreCase("equip"))) {
            player.sendMessage(ChatColor.YELLOW + "Use /ndg shop or /ndg shop <buy|pack> <disc>.");
            return;
        }
        DiscModel model = NDGManager.getInstance().getDiscCatalog().find(args[2]);
        if (model == null) {
            player.sendMessage(ChatColor.RED + "Unknown disc. Open /ndg shop to see available discs.");
            return;
        }
        DiscShop.change(player, model, buy);
    }

    private void decals(Player player, String[] args) {
        if (args.length == 1) {
            DecalShop.open(player);
            return;
        }
        if (args.length != 3 || !List.of("buy", "equip", "preview").contains(args[1].toLowerCase(Locale.ROOT))) {
            player.sendMessage(ChatColor.YELLOW + "Use /ndg decals or /ndg decals <buy|equip|preview> <decal>.");
            return;
        }
        ScoreDecal decal = ScoreDecal.find(args[2]);
        if (decal == null) {
            player.sendMessage(ChatColor.RED + "Unknown score decal. Open /ndg decals to see the selection.");
            return;
        }
        if (args[1].equalsIgnoreCase("preview")) DecalShop.preview(player, decal);
        else DecalShop.change(player, decal, args[1].equalsIgnoreCase("buy"));
    }

    private void resourcePack(Player player, String[] args) {
        try {
            var pack = NDGManager.getInstance().getPacked();
            if (pack.loaded(player)) player.sendMessage(ChatColor.GREEN + "Parkland disc models are active.");
            else if (pack.pending(player)) player.sendMessage(ChatColor.YELLOW + "Waiting for Minecraft to load the disc models.");
            else if (args.length > 1 && args[1].equalsIgnoreCase("retry")) {
                player.sendMessage(pack.send(player) ? ChatColor.GREEN + "Disc models offered again."
                        : ChatColor.YELLOW + "Pack delivery is unavailable for this connection.");
            } else player.sendMessage(ChatColor.GRAY + "Using vanilla disc icons. To try the disc models, use /ndg pack retry.");
        } catch (java.io.IOException | RuntimeException failure) {
            player.sendMessage(ChatColor.YELLOW + "The disc pack is unavailable. Vanilla disc icons are active.");
        }
    }

    private void queuePlayer(Player player, String[] args) {
        RoundFormat selected = args.length >= 2 ? RoundFormat.parse(args[1]) : RoundFormat.FFA;
        if (selected == null) {
            player.sendMessage(ChatColor.RED + formatQueueResult(RoundQueueManager.QueueResult.INVALID_FORMAT));
            return;
        }
        if (!selected.canQueueDirectly()) {
            player.sendMessage(ChatColor.YELLOW + "Use /ndg tournament signup for the weekly tournament.");
            return;
        }
        String roundFormat = selected.id();
        String lobby = args.length >= 3 ? args[2] : null;
        CourseTerrain terrain = args.length == 4 ? CourseTerrain.parse(args[3]) : null;
        if (args.length > 4 || args.length == 4 && (!"new".equalsIgnoreCase(lobby) || terrain == null)) {
            player.sendMessage(ChatColor.RED + formatQueueResult(RoundQueueManager.QueueResult.TERRAIN_UNAVAILABLE));
            return;
        }

        GameRound activeRound = this.findActiveRound(player.getUniqueId());
        if (activeRound != null) {
            player.sendMessage(ChatColor.RED + "You are already in an active round.");
            return;
        }

        GameRound preparingRound = this.findPreparingRound(player.getUniqueId());
        RoundQueueManager queueManager = NDGManager.getInstance().getRoundQueueManager();
        if (preparingRound != null && !queueManager.isRoundQueued(preparingRound)) {
            player.sendMessage(ChatColor.RED + "Your round is preparing. Please wait for the course to finish.");
            return;
        }

        Optional<String> queuedFormat = queueManager.getQueuedRoundFormat(player.getUniqueId());
        if (queuedFormat.map(RoundFormat::parse).orElse(null) == selected) {
            player.sendMessage(ChatColor.YELLOW + "You are already queued for " + this.displayFormat(roundFormat) + ".");
            return;
        }

        Team party = NDGManager.getInstance().getPartyManager().getPartyById(player.getUniqueId());
        if (party != null) {
            if (!party.getOwner().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Only the party leader can queue the party.");
                return;
            }

            this.queueParty(player, party, roundFormat, lobby, terrain);
            return;
        }
        if (lobby != null) {
            this.queueParty(player, null, roundFormat, lobby, terrain);
            return;
        }

        Bukkit.getPluginManager().callEvent(new PlayerQueueRoundEvent(
                player.getUniqueId(),
                queueManager.getQueuedRounds(),
                roundFormat));
    }

    private void leaveQueue(Player player) {
        Team party = NDGManager.getInstance().getPartyManager().getPartyById(player.getUniqueId());
        if (party != null && party.getOwner().equals(player.getUniqueId())) {
            boolean removedAny = false;
            for (UUID playerId : party.getTeamMembers()) {
                removedAny |= NDGManager.getInstance()
                        .getRoundQueueManager()
                        .removePlayerFromQueue(playerId);
            }

            if (removedAny) {
                this.sendPartyMessage(party, ChatColor.GREEN + "Your party left the disc golf queue.");
                return;
            }
        }

        boolean removed = NDGManager.getInstance()
                .getRoundQueueManager()
                .removePlayerFromQueue(player.getUniqueId());

        if (removed) {
            player.sendMessage(ChatColor.GREEN + "You left the disc golf queue.");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "You are not currently queued.");
    }

    private void openMenu(Player player) {
        NDGManager.getInstance()
                .getGuiManager()
                .openGUI(new RoundPickerGUI(), player);
    }

    private void spectating(Player player, String[] args) {
        UUID id = player.getUniqueId();
        if (!(findActiveRound(id) instanceof RoundImpl round)) {
            player.sendMessage(ChatColor.RED + "Camera and replay settings are available during a round.");
            return;
        }
        var camera = round.play().spectating();
        boolean live = args[0].equalsIgnoreCase("camera");
        String label = live ? "Disc camera" : "Instant replays";
        if (!(live ? camera.cameraAvailable() : camera.replaysAvailable())) {
            player.sendMessage(ChatColor.RED + label + " disabled by the server.");
            return;
        }
        if (args.length == 2 && (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            boolean enabled = args[1].equalsIgnoreCase("on");
            if (live) camera.follow(player, enabled);
            else camera.watchReplays(id, enabled);
        } else if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Use /ndg " + args[0].toLowerCase(Locale.ROOT) + " <on|off>.");
            return;
        }
        boolean enabled = live ? camera.follows(id) : camera.watchesReplays(id);
        player.sendMessage(ChatColor.AQUA + label + ": " + (enabled ? "on" : "off") + " for this round.");
    }

    private void sendStatus(Player player) {
        GameRound activeRound = this.findActiveRound(player.getUniqueId());
        if (activeRound != null) {
            player.sendMessage(ChatColor.GREEN + "You are in an active round.");
            if (activeRound instanceof MatchPlayRound match) {
                player.sendMessage(ChatColor.GRAY + "Mode: Match Play");
                player.sendMessage(ChatColor.GOLD + match.scoreLine());
            }
            if (activeRound instanceof EliminationRound elimination) {
                player.sendMessage(ChatColor.GRAY + "Mode: Elimination");
                player.sendMessage(ChatColor.GOLD + elimination.stageLine());
            }
            if (activeRound instanceof ArcadeRound arcade) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Mode: Arcade");
                player.sendMessage(ChatColor.GRAY + arcade.chargeSummary(player.getUniqueId()));
            }
            if (activeRound instanceof AceRaceRound race) {
                Team side = race.play().team(player.getUniqueId());
                player.sendMessage(ChatColor.GRAY + "Mode: Ace Race");
                player.sendMessage(ChatColor.GOLD + "Aces: " + race.getScoreCardByTeam(side).getHoledOutCount()
                        + " • This hole: " + race.holeResult(side));
            }
            if (activeRound instanceof ClosestToPinRound closest) {
                Team side = closest.play().team(player.getUniqueId());
                player.sendMessage(ChatColor.GRAY + "Mode: Closest to Pin");
                player.sendMessage(ChatColor.GOLD + "Total: " + closest.total(side).label()
                        + " • This hole: " + closest.holeResult(side));
            }
            RoundFormat mode = RoundFormat.of(activeRound);
            if (mode != null && mode.usesPairedAttempts()) player.sendMessage(ChatColor.GRAY + "Mode: " + mode.label());
            if (mode == RoundFormat.ONE_DISC && activeRound instanceof normalmanv2.normalDiscGolf.impl.round.RoundImpl round) {
                var locked = round.play().lockedDisc(player.getUniqueId());
                player.sendMessage(ChatColor.GRAY + "Mode: " + mode.label());
                player.sendMessage(ChatColor.GRAY + (locked == null ? "Choose your disc in /ndg bag."
                        : "Locked disc: " + locked.displayName()));
            }
            if (mode == RoundFormat.DISC_ROULETTE && activeRound instanceof normalmanv2.normalDiscGolf.impl.round.RoundImpl round) {
                var drawn = round.play().rouletteDisc(player.getUniqueId());
                player.sendMessage(ChatColor.GRAY + "Mode: " + mode.label());
                player.sendMessage(ChatColor.GRAY + (drawn == null ? "Waiting for your next draw."
                        : "Drawn disc: " + drawn.displayName()));
            }
            player.sendMessage(ChatColor.GRAY + "Hole: " + (activeRound.getCurrentHole() + 1) + "/" + activeRound.getCourse().holes());
            if (activeRound instanceof normalmanv2.normalDiscGolf.impl.round.RoundImpl round)
                player.sendMessage(ChatColor.AQUA + round.play().courseEvents().current().status());
            if (activeRound.getCourse() instanceof normalmanv2.normalDiscGolf.impl.course.CourseImpl course) {
                player.sendMessage(ChatColor.AQUA + (activeRound instanceof DailyRound daily ? daily.definition().description() : course.generationInfo()));
                player.sendMessage(ChatColor.GRAY + "Layout: " + course.holeName(activeRound.getCurrentHole()));
            }
            player.sendMessage(ChatColor.GRAY + "Players: " + this.countPlayers(activeRound));
            player.sendMessage(ChatColor.GRAY + "Round: " + activeRound.getId());
            return;
        }

        GameRound preparingRound = this.findPreparingRound(player.getUniqueId());
        if (preparingRound != null) {
            player.sendMessage(ChatColor.YELLOW + "Your round is preparing.");
            if (preparingRound.getCourse() instanceof normalmanv2.normalDiscGolf.impl.course.CourseImpl course)
                player.sendMessage(ChatColor.AQUA + course.generationInfo());
            player.sendMessage(ChatColor.GRAY + "Round: " + preparingRound.getId());
            player.sendMessage(ChatColor.GRAY + "Players: " + this.countPlayers(preparingRound));
            player.sendMessage(ChatColor.GRAY + "You will be teleported when the course is ready.");
            return;
        }

        var queue = NDGManager.getInstance().getRoundQueueManager();
        var custom = queue.getQueuedCustomCourse(player.getUniqueId());
        if (custom.isPresent()) {
            player.sendMessage(ChatColor.YELLOW + "You are queued for custom course " + custom.get().name() + ".");
            return;
        }
        Optional<String> queuedFormat = queue.getQueuedRoundFormat(player.getUniqueId());

        if (queuedFormat.isPresent()) {
            player.sendMessage(ChatColor.YELLOW + "You are queued for " + this.displayFormat(queuedFormat.get()) + ".");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "You are not in a round or queue.");
    }

    private void sendScore(Player player) {
        GameRound activeRound = this.findActiveRound(player.getUniqueId());
        if (activeRound == null) {
            player.sendMessage(ChatColor.RED + "You are not in an active round.");
            return;
        }
        if (activeRound instanceof MatchPlayRound match) {
            sendMatchScore(player, match);
            return;
        }
        if (activeRound instanceof AceRaceRound race) {
            player.sendMessage(ChatColor.GREEN + "Ace Race scorecard");
            player.sendMessage(ChatColor.GRAY + "Hole: " + (race.getCurrentHole() + 1) + "/" + race.getCourse().holes());
            for (var entry : race.standings()) player.sendMessage(ChatColor.GOLD + entry.line());
            return;
        }
        if (activeRound instanceof ClosestToPinRound closest) {
            player.sendMessage(ChatColor.GREEN + "Closest to Pin scorecard");
            player.sendMessage(ChatColor.GRAY + "Hole: " + (closest.getCurrentHole() + 1) + "/" + closest.getCourse().holes());
            for (var entry : closest.standings()) player.sendMessage(ChatColor.GOLD + entry.line());
            return;
        }
        if (activeRound instanceof EliminationRound elimination) {
            int hole = elimination.getCurrentHole();
            player.sendMessage(ChatColor.GREEN + "Elimination scorecard");
            player.sendMessage(ChatColor.GRAY + "Hole: " + (hole + 1) + "/" + elimination.getCourse().holes());
            player.sendMessage(ChatColor.GOLD + elimination.stageLine());
            for (Team team : elimination.getTeams()) {
                var card = elimination.getScoreCardByTeam(team);
                var finished = card.getHoleScores().get(hole);
                String result = elimination.isSafe(team) ? "safe"
                        : finished == null ? card.getCurrentStrokes() + " so far" : finished + " (finished)";
                player.sendMessage(ChatColor.GRAY + formatTeamMembers(team) + ": " + result);
            }
            return;
        }

        Team playerTeam = this.findPlayerTeam(activeRound, player.getUniqueId());
        ScoreCard scoreCard = activeRound.getScoreCardById(player.getUniqueId());
        if (scoreCard == null) {
            player.sendMessage(ChatColor.YELLOW + "Your scorecard is not ready yet.");
            return;
        }

        boolean teamRound = playerTeam != null && playerTeam.getMaximumPlayers() > 1;
        RoundFormat mode = RoundFormat.of(activeRound);
        String title = mode != null && (mode.usesPairedAttempts() || mode == RoundFormat.ONE_DISC
                || mode == RoundFormat.DISC_ROULETTE || mode == RoundFormat.ARCADE)
                ? mode.label() + " scorecard"
                : teamRound ? "Doubles team scorecard" : "Disc golf scorecard";
        player.sendMessage(ChatColor.GREEN + title);
        if (teamRound) {
            player.sendMessage(ChatColor.GRAY + "Team: " + this.formatTeamMembers(playerTeam));
        }
        player.sendMessage(ChatColor.GRAY + "Hole: " + (activeRound.getCurrentHole() + 1) + "/" + activeRound.getCourse().holes());
        player.sendMessage(ChatColor.GRAY + (teamRound ? "Current hole team strokes: " : "Current hole strokes: ") + scoreCard.getCurrentStrokes());
        player.sendMessage(ChatColor.GRAY + (teamRound ? "Total team strokes: " : "Total strokes: ") + scoreCard.getTotalStrokes());
        player.sendMessage(ChatColor.GRAY + "Score: " + this.formatRelativeScore(scoreCard.getTotalScore()));

        if (!scoreCard.getHoleScores().isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Completed holes: " + this.formatHoleScores(scoreCard));
        }

        if (activeRound.getScoreCards().size() > 1) {
            player.sendMessage(ChatColor.GRAY + "Standings:");
            activeRound.getScoreCards()
                    .entrySet()
                    .stream()
                    .sorted(Comparator
                            .comparingInt((Map.Entry<Team, ScoreCard> entry) -> entry.getValue().getTotalScore())
                            .thenComparingInt(entry -> entry.getValue().getTotalStrokes())
                            .thenComparing(entry -> this.formatTeamMembers(entry.getKey())))
                    .forEach(entry -> player.sendMessage(ChatColor.GRAY
                            + "- "
                            + this.formatTeamMembers(entry.getKey())
                            + ": "
                            + this.formatRelativeScore(entry.getValue().getTotalScore())
                            + " ("
                            + entry.getValue().getTotalStrokes()
                            + " strokes)"));
        }
    }

    private void sendProfile(Player player) {
        PlayerData playerData = NDGManager.getInstance()
                .getPlayerDataManager()
                .getOrCreatePlayerData(player.getUniqueId());
        PDGARating rating = playerData.getRating();

        player.sendMessage(ChatColor.GREEN + "Disc golf career");
        var division = rating.getCareerDivision();
        player.sendMessage(ChatColor.GOLD + "Division: " + division.label());
        player.sendMessage(ChatColor.GRAY + "Rating: " + (rating.getRating() == 0 ? "Unrated" : rating.getRating()));
        player.sendMessage(ChatColor.GRAY + "Rated rounds: " + rating.getTotalRounds());
        if (rating.getPeakRating() > 0) player.sendMessage(ChatColor.GRAY + "Peak rating: " + rating.getPeakRating());
        if (rating.getLastRoundRating() > 0) player.sendMessage(ChatColor.GRAY + "Last round rating: " + rating.getLastRoundRating());
        if (division.next() != null)
            player.sendMessage(ChatColor.GRAY + "Next division: " + division.next().label() + " at " + division.next().minimum());
        player.sendMessage(ChatColor.GOLD + "Disc shop balance: " + playerData.getDiscBag().credits() + " credits");
        player.sendMessage(ChatColor.GRAY + "Average score (18-hole pace): " + this.formatDecimal(rating.getAverageScore()));
        player.sendMessage(ChatColor.GRAY + "Skills:");
        this.sendSkillSummary(player, playerData.getSkills());
    }

    private void sendSkillSummary(Player player, PlayerSkills playerSkills) {
        for (String skill : List.of("power", "accuracy", "putting", "recovery", "forehand", "backhand",
                "wind-reading", "mental-focus", "form", "flex", "roller", "tomahawk", "thumber")) {
            String label = skill.equals("power") ? "Driving" : formatEnumName(skill);
            this.sendSkill(player, label, playerSkills.asMap().get(skill));
        }
    }

    private void sendSkill(Player player, String name, Skill skill) {
        String xpProgress = skill.getLevel() >= skill.getMaxLevel()
                ? "max"
                : this.formatDecimal(skill.getXp()) + "/" + this.formatDecimal(skill.getXpToNext()) + " XP";
        player.sendMessage(ChatColor.GRAY
                + "- "
                + name
                + ": "
                + skill.getLevel()
                + "/"
                + skill.getMaxLevel()
                + " ("
                + xpProgress
                + ")");
    }

    private void handleParty(Player player, String[] args) {
        if (args.length < 2) {
            this.sendPartyStatus(player);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "invite" -> this.inviteToParty(player, args);
            case "accept" -> this.acceptPartyInvite(player);
            case "decline", "deny", "reject" -> this.declinePartyInvite(player);
            case "leave" -> this.leaveParty(player);
            case "status" -> this.sendPartyStatus(player);
            default -> this.sendPartyHelp(player);
        }
    }

    private void inviteToParty(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Use /ndg party invite <player>.");
            return;
        }

        Player invitee = Bukkit.getPlayer(args[2]);
        if (invitee == null) {
            player.sendMessage(ChatColor.RED + "That player is not online.");
            return;
        }

        if (invitee.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You cannot invite yourself.");
            return;
        }

        if (this.isBusy(player.getUniqueId()) || this.isBusy(invitee.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Party invites are only available before queueing or starting a round.");
            return;
        }

        PartyManager partyManager = NDGManager.getInstance().getPartyManager();
        Team party = partyManager.getPartyById(player.getUniqueId());
        if (party != null && !party.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the party leader can invite players.");
            return;
        }

        if (party != null && party.isFull()) {
            player.sendMessage(ChatColor.RED + "Your party is full.");
            return;
        }

        if (partyManager.getPartyById(invitee.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + invitee.getName() + " is already in a party.");
            return;
        }

        NDGManager.getInstance()
                .getInviteService()
                .createInvite(player.getUniqueId(), invitee.getUniqueId(), PARTY_INVITE_DURATION);

        player.sendMessage(ChatColor.GREEN + "Party invite sent to " + invitee.getName() + ".");
        invitee.sendMessage(ChatColor.GREEN
                + player.getName()
                + " invited you to a disc golf party. Use /ndg party accept or /ndg party decline.");
    }

    private void acceptPartyInvite(Player player) {
        Optional<Invite> invite = NDGManager.getInstance()
                .getInviteService()
                .consumeInvite(player.getUniqueId());

        if (invite.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You do not have a pending party invite.");
            return;
        }

        Player inviter = Bukkit.getPlayer(invite.get().inviter());
        if (inviter == null) {
            player.sendMessage(ChatColor.RED + "The player who invited you is no longer online.");
            return;
        }

        if (this.isBusy(player.getUniqueId()) || this.isBusy(inviter.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Party invites must be accepted before queueing or starting a round.");
            return;
        }

        PartyManager partyManager = NDGManager.getInstance().getPartyManager();
        if (partyManager.getPartyById(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.YELLOW + "You are already in a party.");
            return;
        }

        Team party = partyManager.getPartyById(inviter.getUniqueId());
        if (party == null) {
            party = new TeamImpl(inviter.getUniqueId(), Constants.MAXIMUM_PARTY_MEMBERS);
            partyManager.addParty(party);
        }

        if (party.isFull()) {
            player.sendMessage(ChatColor.RED + "That party is full.");
            return;
        }

        party.addPlayer(player.getUniqueId());
        this.sendPartyMessage(party, ChatColor.GREEN + player.getName() + " joined the party.");
    }

    private void declinePartyInvite(Player player) {
        boolean declined = NDGManager.getInstance()
                .getInviteService()
                .rejectInvite(player.getUniqueId());

        player.sendMessage(declined
                ? ChatColor.GREEN + "Party invite declined."
                : ChatColor.YELLOW + "You do not have a pending party invite.");
    }

    private void leaveParty(Player player) {
        PartyManager partyManager = NDGManager.getInstance().getPartyManager();
        Team party = partyManager.getPartyById(player.getUniqueId());
        if (party == null) {
            player.sendMessage(ChatColor.YELLOW + "You are not in a party.");
            return;
        }

        boolean leaderLeaving = party.getOwner().equals(player.getUniqueId());
        partyManager.leaveParty(player.getUniqueId());

        if (leaderLeaving) {
            this.sendPartyMessage(party, ChatColor.YELLOW + "The party was disbanded.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "You left the party.");
        this.sendPartyMessage(party, ChatColor.YELLOW + player.getName() + " left the party.");
    }

    private void sendPartyStatus(Player player) {
        Team party = NDGManager.getInstance().getPartyManager().getPartyById(player.getUniqueId());
        if (party == null) {
            player.sendMessage(ChatColor.YELLOW + "You are not in a party.");
            player.sendMessage(ChatColor.GRAY + "/ndg party invite <player> - Invite a player.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Party");
        player.sendMessage(ChatColor.GRAY + "Leader: " + this.formatPlayerName(party.getOwner()));
        player.sendMessage(ChatColor.GRAY + "Members: " + this.formatTeamMembers(party));
    }

    private void sendPartyHelp(Player player) {
        player.sendMessage(ChatColor.GRAY + "/ndg party invite <player> - Invite a player.");
        player.sendMessage(ChatColor.GRAY + "/ndg party accept - Accept an invite.");
        player.sendMessage(ChatColor.GRAY + "/ndg party decline - Decline an invite.");
        player.sendMessage(ChatColor.GRAY + "/ndg party leave - Leave or disband your party.");
        player.sendMessage(ChatColor.GRAY + "/ndg party status - Show your party.");
    }

    private void queueParty(Player player, Team party, String roundFormat, String lobby, CourseTerrain terrain) {
        RoundQueueManager.QueueResult result = NDGManager.getInstance()
                .getRoundQueueManager()
                .addPartyToQueue(party == null ? List.of(player.getUniqueId()) : party.getTeamMembers(), roundFormat, lobby, terrain);

        if (result == RoundQueueManager.QueueResult.SUCCESS) {
            String message = " been queued for " + this.displayFormat(roundFormat) + ".";
            if (party == null) player.sendMessage(ChatColor.GREEN + "You have" + message);
            else this.sendPartyMessage(party, ChatColor.GREEN + "Your party has" + message);
            return;
        }

        player.sendMessage(ChatColor.RED + this.formatQueueResult(result));
    }

    private void cancelRound(Player player) {
        GameRound activeRound = this.findActiveRound(player.getUniqueId());
        GameRound round = activeRound != null ? activeRound : this.findPreparingRound(player.getUniqueId());
        if (round == null) {
            player.sendMessage(ChatColor.RED + "You are not in an active or preparing round.");
            return;
        }

        boolean canCancel = player.hasPermission("ndg.commands.cancel") || this.countPlayers(round) <= 1;
        if (!canCancel) {
            player.sendMessage(ChatColor.RED + "Only a solo player or someone with cancel permission can cancel this round.");
            return;
        }

        NDGManager.getInstance().getRoundHandler().cancelRound(round);
        player.sendMessage(ChatColor.GREEN + "Round canceled.");
    }

    private GameRound findActiveRound(UUID playerId) {
        return NDGManager.getInstance()
                .getRoundHandler()
                .getActiveRounds()
                .stream()
                .filter(round -> round.containsPlayer(playerId))
                .findFirst()
                .orElse(null);
    }

    private GameRound findPreparingRound(UUID playerId) {
        return NDGManager.getInstance()
                .getRoundHandler()
                .getPreparingRounds()
                .stream()
                .filter(round -> round.containsPlayer(playerId))
                .findFirst()
                .orElse(null);
    }

    private int countPlayers(GameRound round) {
        int players = 0;
        for (Team team : round.getTeams()) {
            players += team.getTeamMembers().size();
        }
        return players;
    }

    private boolean isBusy(UUID playerId) {
        return this.findActiveRound(playerId) != null
                || this.findPreparingRound(playerId) != null
                || NDGManager.getInstance().getRoundQueueManager().isPlayerQueued(playerId);
    }

    private void sendPartyMessage(Team party, String message) {
        for (UUID playerId : party.getTeamMembers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private Team findPlayerTeam(GameRound round, UUID playerId) {
        for (Team team : round.getTeams()) {
            if (team.contains(playerId)) {
                return team;
            }
        }

        return null;
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

    private String formatHoleScores(ScoreCard scoreCard) {
        return scoreCard.getHoleScores()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "Hole " + (entry.getKey() + 1) + ": " + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private void sendMatchScore(Player player, MatchPlayRound match) {
        int hole = match.getCurrentHole();
        player.sendMessage(ChatColor.GREEN + "Match Play scorecard");
        player.sendMessage(ChatColor.GRAY + "Hole: " + (hole + 1) + "/" + match.getCourse().holes());
        player.sendMessage(ChatColor.GOLD + match.scoreLine());
        player.sendMessage(ChatColor.GRAY + match.resultsLine());
        for (UUID id : List.of(match.score().first(), match.score().second())) {
            ScoreCard card = match.getScoreCardById(id);
            Integer finished = card.getHoleScores().get(hole);
            player.sendMessage(ChatColor.GRAY + match.playerName(id) + ": "
                    + (finished == null ? MatchPlayRound.strokeCount(card.getCurrentStrokes()) + " this hole"
                    : MatchPlayRound.strokeCount(finished) + " (holed out)"));
        }
        for (var result : match.score().holes()) player.sendMessage(ChatColor.GRAY + match.describe(result));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "NormalDiscGolf commands");
        sender.sendMessage(ChatColor.GRAY + "/ndg queue <" + queueFormats("|") + "> - Join the round queue.");
        sender.sendMessage(ChatColor.GRAY + "/ndg lie <1|2> - Choose a lie in Best Shot Doubles.");
        sender.sendMessage(ChatColor.GRAY + "/ndg leave - Leave the queue.");
        sender.sendMessage(ChatColor.GRAY + "/ndg menu - Open the round picker.");
        sender.sendMessage(ChatColor.GRAY + "/ndg status - Show your queue or round status.");
        sender.sendMessage(ChatColor.GRAY + "/ndg score - Show your scorecard.");
        sender.sendMessage(ChatColor.GRAY + "/ndg summary - Show your most recent completed round.");
        sender.sendMessage(ChatColor.GRAY + "/ndg career - Show your division, rating, and skills.");
        sender.sendMessage(ChatColor.GRAY + "/ndg shop - Browse rotating stock and buy discs.");
        sender.sendMessage(ChatColor.GRAY + "/ndg bag - Pack owned discs or select one during a round.");
        sender.sendMessage(ChatColor.GRAY + "/ndg shot - Choose a throw stance, set angle, timing and preview, or check wind.");
        sender.sendMessage(ChatColor.GRAY + "/ndg camera <on|off> - Follow the disc from behind during throws.");
        sender.sendMessage(ChatColor.GRAY + "/ndg replay <on|off> - Watch automatic ace and long putt replays.");
        sender.sendMessage(ChatColor.GRAY + "/ndg clubhouse - Daily challenge, records and medals.");
        sender.sendMessage(ChatColor.GRAY + "/ndg course - Build, list, or play saved custom courses.");
        sender.sendMessage(ChatColor.GRAY + "/ndg daily - See today's leaderboard or use /ndg daily play.");
        sender.sendMessage(ChatColor.GRAY + "/ndg tournament - See the weekly schedule, sign up, or open standings.");
        sender.sendMessage(ChatColor.GRAY + "/ndg season - See ranked ratings, placement prizes, and season history.");
        sender.sendMessage(ChatColor.GRAY + "/ndg pack [retry] - Check or retry the disc models.");
        sender.sendMessage(ChatColor.GRAY + "/ndg decals - Preview, unlock, and equip score decals.");
        if (sender.hasPermission(ProfileAdminCommand.PERMISSION)) {
            ProfileAdminCommand.help(sender);
        }
        sender.sendMessage(ChatColor.GRAY + "/ndg party <invite|accept|decline|leave|status> - Manage your party.");
        sender.sendMessage(ChatColor.GRAY + "/ndg cancel - Cancel your solo round.");
        sender.sendMessage(ChatColor.GRAY + "/ndg skip | lie | concede | quit - Exit the camera or preview, return to lie, concede, or forfeit.");
        sender.sendMessage(ChatColor.GRAY + "/throwdisc <putter|midrange|driver> <backhand|forehand> - Throw on your turn.");
    }

    private String displayFormat(String format) {
        return RoundFormat.parse(format).label();
    }

    private String formatRelativeScore(int score) {
        if (score > 0) {
            return "+" + score;
        }
        return String.valueOf(score);
    }

    private String formatDecimal(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((int) value);
        }

        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String formatEnumName(String value) {
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[-_]"))
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String queueFormats(String separator) {
        return String.join(separator, Arrays.stream(RoundFormat.values()).filter(RoundFormat::canQueueDirectly).map(RoundFormat::id).toList());
    }

    private String formatQueueResult(RoundQueueManager.QueueResult result) {
        return switch (result) {
            case INVALID_FORMAT -> "Unknown round format. Available formats: " + queueFormats(", ") + ".";
            case RESOURCE_PACK_REQUIRED -> "A party member must finish loading the server resource pack.";
            case REQUIRED_ASSETS_MISSING -> "Disc golf courses are not ready: "
                    + NDGManager.getInstance().getFileManager().getMissingRequiredObstaclesMessage();
            case PLAYER_DATA_MISSING -> "A party member is missing player data. Have them rejoin and try again.";
            case PLAYER_OFFLINE -> "All party members must be online before queueing.";
            case PLAYER_IN_ACTIVE_ROUND -> "A party member is already in an active or preparing round.";
            case PLAYER_ALREADY_QUEUED -> "A party member is already queued.";
            case DIVISION_MISMATCH -> "All party members must be in the same division to queue together.";
            case PARTY_TOO_LARGE -> "That party is too large for this round format.";
            case ROUND_FULL -> "No queued round has enough space for your party yet.";
            case CUSTOM_COURSE_BUSY -> "A round is already using that course.";
            case DAILY_UNAVAILABLE -> "The Daily Course is currently unavailable.";
            case LOBBY_UNAVAILABLE -> "That lobby has already started or closed. Choose an open lobby.";
            case TERRAIN_UNAVAILABLE -> "That terrain is unavailable for this mode.";
            case SUCCESS -> "Queued.";
        };
    }

    private List<String> matching(String prefix, List<String> options) {
        List<String> matches = new ArrayList<>();
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
