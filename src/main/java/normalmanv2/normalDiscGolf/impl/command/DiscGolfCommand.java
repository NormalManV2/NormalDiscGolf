package normalmanv2.normalDiscGolf.impl.command;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.command.AbstractCommand;
import normalmanv2.normalDiscGolf.common.invite.Invite;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.cosmetic.ScoreDecal;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.gui.DiscShop;
import normalmanv2.normalDiscGolf.impl.gui.DecalShop;
import normalmanv2.normalDiscGolf.impl.event.PlayerQueueRoundEvent;
import normalmanv2.normalDiscGolf.impl.gui.round.RoundPickerGUI;
import normalmanv2.normalDiscGolf.impl.manager.party.PartyManager;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager;
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
            case "profile", "stats" -> this.sendProfile(player);
            case "shop" -> this.shop(player, args);
            case "decals" -> this.decals(player, args);
            case "party" -> this.handleParty(player, args);
            case "cancel" -> this.cancelRound(player);
            case "skip" -> normalmanv2.normalDiscGolf.impl.round.RoundPresentation.cancel(player);
            case "lie" -> {
                GameRound active = this.findActiveRound(player.getUniqueId());
                if (active instanceof normalmanv2.normalDiscGolf.impl.round.RoundImpl impl) {
                    if (impl.play().inFlight()) { player.sendMessage("§cWait for the disc to land."); break; }
                    normalmanv2.normalDiscGolf.impl.round.RoundPresentation.cancel(player);
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
            return this.matching(args[0], List.of("queue", "leave", "menu", "status", "score", "profile", "shop", "decals", "party", "cancel", "skip", "lie", "concede", "quit", "help"));
        }

        if (args[0].equalsIgnoreCase("shop")) {
            if (args.length == 2) return matching(args[1], List.of("buy", "equip"));
            if (args.length == 3) return matching(args[2], Arrays.stream(DiscModel.values()).map(DiscModel::id).toList());
        }
        if (args[0].equalsIgnoreCase("decals")) {
            if (args.length == 2) return matching(args[1], List.of("buy", "equip", "preview"));
            if (args.length == 3) return matching(args[2], Arrays.stream(ScoreDecal.values()).map(ScoreDecal::id).toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("queue")) {
            return this.matching(args[1], List.of("ffa", "doubles"));
        }

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
        if (args.length != 3 || (!buy && !args[1].equalsIgnoreCase("equip"))) {
            player.sendMessage(ChatColor.YELLOW + "Use /ndg shop or /ndg shop <buy|equip> <disc>.");
            return;
        }
        DiscModel model = DiscModel.find(args[2]);
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

    private void queuePlayer(Player player, String[] args) {
        String roundFormat = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "ffa";
        if (!roundFormat.equals("ffa") && !roundFormat.equals("doubles")) {
            player.sendMessage(ChatColor.RED + "Unknown round format. Use ffa or doubles.");
            return;
        }

        GameRound activeRound = this.findActiveRound(player.getUniqueId());
        if (activeRound != null) {
            player.sendMessage(ChatColor.RED + "You are already in an active round.");
            return;
        }

        GameRound preparingRound = this.findPreparingRound(player.getUniqueId());
        if (preparingRound != null) {
            player.sendMessage(ChatColor.RED + "Your round is preparing. Please wait for the course to finish.");
            return;
        }

        RoundQueueManager queueManager = NDGManager.getInstance().getRoundQueueManager();
        Optional<String> queuedFormat = queueManager.getQueuedRoundFormat(player.getUniqueId());
        if (queuedFormat.isPresent() && queuedFormat.get().equalsIgnoreCase(roundFormat)) {
            player.sendMessage(ChatColor.YELLOW + "You are already queued for " + this.displayFormat(roundFormat) + ".");
            return;
        }

        Team party = NDGManager.getInstance().getPartyManager().getPartyById(player.getUniqueId());
        if (party != null) {
            if (!party.getOwner().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Only the party leader can queue the party.");
                return;
            }

            this.queueParty(player, party, roundFormat);
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

    private void sendStatus(Player player) {
        GameRound activeRound = this.findActiveRound(player.getUniqueId());
        if (activeRound != null) {
            player.sendMessage(ChatColor.GREEN + "You are in an active round.");
            player.sendMessage(ChatColor.GRAY + "Hole: " + (activeRound.getCurrentHole() + 1) + "/" + activeRound.getCourse().holes());
            player.sendMessage(ChatColor.GRAY + "Players: " + this.countPlayers(activeRound));
            player.sendMessage(ChatColor.GRAY + "Round: " + activeRound.getId());
            return;
        }

        GameRound preparingRound = this.findPreparingRound(player.getUniqueId());
        if (preparingRound != null) {
            player.sendMessage(ChatColor.YELLOW + "Your round is preparing.");
            player.sendMessage(ChatColor.GRAY + "Round: " + preparingRound.getId());
            player.sendMessage(ChatColor.GRAY + "Players: " + this.countPlayers(preparingRound));
            player.sendMessage(ChatColor.GRAY + "You will be teleported when the course is ready.");
            return;
        }

        Optional<String> queuedFormat = NDGManager.getInstance()
                .getRoundQueueManager()
                .getQueuedRoundFormat(player.getUniqueId());

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

        Team playerTeam = this.findPlayerTeam(activeRound, player.getUniqueId());
        ScoreCard scoreCard = activeRound.getScoreCardById(player.getUniqueId());
        if (scoreCard == null) {
            player.sendMessage(ChatColor.YELLOW + "Your scorecard is not ready yet.");
            return;
        }

        boolean teamRound = playerTeam != null && playerTeam.getMaximumPlayers() > 1;
        player.sendMessage(ChatColor.GREEN + (teamRound ? "Doubles team scorecard" : "Disc golf scorecard"));
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

        player.sendMessage(ChatColor.GREEN + "Disc golf profile");
        player.sendMessage(ChatColor.GRAY + "Division: " + this.formatEnumName(rating.getDivision().name()));
        player.sendMessage(ChatColor.GRAY + "Rating: " + rating.getRating());
        player.sendMessage(ChatColor.GRAY + "Rounds: " + rating.getTotalRounds());
        player.sendMessage(ChatColor.GOLD + "Disc shop balance: " + playerData.getDiscBag().credits() + " credits");
        player.sendMessage(ChatColor.GRAY + "Average score: " + this.formatDecimal(rating.getAverageScore()));
        player.sendMessage(ChatColor.GRAY + "Skills:");
        this.sendSkillSummary(player, playerData.getSkills());
    }

    private void sendSkillSummary(Player player, PlayerSkills playerSkills) {
        this.sendSkill(player, "Accuracy", playerSkills.getAccuracy());
        this.sendSkill(player, "Backhand", playerSkills.getBackhand());
        this.sendSkill(player, "Forehand", playerSkills.getForehand());
        this.sendSkill(player, "Form", playerSkills.getForm());
        this.sendSkill(player, "Power", playerSkills.getPower());
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

    private void queueParty(Player player, Team party, String roundFormat) {
        RoundQueueManager.QueueResult result = NDGManager.getInstance()
                .getRoundQueueManager()
                .addPartyToQueue(party.getTeamMembers(), roundFormat);

        if (result == RoundQueueManager.QueueResult.SUCCESS) {
            this.sendPartyMessage(party, ChatColor.GREEN + "Your party has been queued for " + this.displayFormat(roundFormat) + ".");
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "NormalDiscGolf commands");
        sender.sendMessage(ChatColor.GRAY + "/ndg queue <ffa|doubles> - Join the round queue.");
        sender.sendMessage(ChatColor.GRAY + "/ndg leave - Leave the queue.");
        sender.sendMessage(ChatColor.GRAY + "/ndg menu - Open the round picker.");
        sender.sendMessage(ChatColor.GRAY + "/ndg status - Show your queue or round status.");
        sender.sendMessage(ChatColor.GRAY + "/ndg score - Show your scorecard.");
        sender.sendMessage(ChatColor.GRAY + "/ndg profile - Show your rating and skills.");
        sender.sendMessage(ChatColor.GRAY + "/ndg shop - Unlock and equip discs.");
        sender.sendMessage(ChatColor.GRAY + "/ndg decals - Preview, unlock, and equip score decals.");
        if (sender.hasPermission(ProfileAdminCommand.PERMISSION)) {
            ProfileAdminCommand.help(sender);
        }
        sender.sendMessage(ChatColor.GRAY + "/ndg party <invite|accept|decline|leave|status> - Manage your party.");
        sender.sendMessage(ChatColor.GRAY + "/ndg cancel - Cancel your solo round.");
        sender.sendMessage(ChatColor.GRAY + "/ndg skip | lie | concede | quit - Skip preview, return to lie, concede hole, or forfeit round.");
        sender.sendMessage(ChatColor.GRAY + "/throwdisc <putter|midrange|driver> <backhand|forehand> - Throw on your turn.");
    }

    private String displayFormat(String format) {
        return format.equalsIgnoreCase("doubles") ? "Doubles" : "FFA";
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
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("_"))
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String formatQueueResult(RoundQueueManager.QueueResult result) {
        return switch (result) {
            case INVALID_FORMAT -> "Unknown round format. Use ffa or doubles.";
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
