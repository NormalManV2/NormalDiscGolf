package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import normalmanv2.normalDiscGolf.impl.resourcepack.RoundHudSkin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import java.util.*;

public final class RoundHud {
    private static final int MAX_LINES = 15;
    private static final RoundHudSkin[] ROW_BADGES = {RoundHudSkin.HOLE, RoundHudSkin.DISTANCE, RoundHudSkin.STROKES,
            RoundHudSkin.SCORE, RoundHudSkin.TURN, RoundHudSkin.TIME, RoundHudSkin.STANDINGS};
    private final RoundImpl round;
    private final Map<UUID, Sidebar> boards = new HashMap<>();

    private static final class Sidebar {
        private final Scoreboard original;
        private final Scoreboard board;
        private final Objective objective;
        private int visibleRows;
        private boolean textured;

        private Sidebar(Player player, String title, boolean textured) {
            original = player.getScoreboard();
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            this.textured = textured;
            objective = board.registerNewObjective("ndg", Criteria.DUMMY, displayTitle(title, textured));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            player.setScoreboard(board);
        }

        private static String displayTitle(String title, boolean textured) {
            return textured ? RoundHudSkin.BANNER.label("§a§l" + ChatColor.stripColor(title)) : title;
        }

        private void update(List<String> lines, String title, boolean textured) {
            if (this.textured != textured) {
                this.textured = textured;
                objective.setDisplayName(displayTitle(title, textured));
            }
            int count = Math.min(MAX_LINES, lines.size());
            for (int i = 0; i < count; i++) {
                String entry = "§" + Integer.toHexString(i);
                String rowId = "ndg" + i;
                var team = board.getTeam(rowId);
                if (team == null) {
                    team = board.registerNewTeam(rowId);
                    team.addEntry(entry);
                }
                String text = textured && i < ROW_BADGES.length ? ROW_BADGES[i].label(lines.get(i)) : lines.get(i);
                if (!text.equals(team.getPrefix())) team.setPrefix(text);
                if (i >= visibleRows) objective.getScore(entry).setScore(MAX_LINES - i);
            }
            for (int i = count; i < visibleRows; i++) board.resetScores("§" + Integer.toHexString(i));
            visibleRows = count;
        }
    }

    public RoundHud(RoundImpl round) { this.round = round; }
    public void update(long seconds) {
        var play = round.play();
        UUID active = play.thrower(play.currentTeam());
        Player thrower = active == null ? null : Bukkit.getPlayer(active);
        MatchPlayRound match = round instanceof MatchPlayRound mode ? mode : null;
        EliminationRound elimination = round instanceof EliminationRound mode ? mode : null;
        AceRaceRound aceRace = round instanceof AceRaceRound mode ? mode : null;
        ClosestToPinRound closest = round instanceof ClosestToPinRound mode ? mode : null;
        SingleThrowRound single = round instanceof SingleThrowRound mode ? mode : null;
        var aceStandings = aceRace == null ? List.<AceRaceRound.Standing>of() : aceRace.standings();
        var closestStandings = closest == null ? List.<ClosestToPinRound.Standing>of() : closest.standings();
        var choosing = play.choosingLie();
        boolean paired = play.usesPairedAttempts();
        String title = paired || play.usesOneDisc() || play.usesRoulette()
                || elimination != null || single != null || round instanceof ArcadeRound || round instanceof CompetitionRound
                ? "§2§l" + RoundFormat.of(round).label().toUpperCase(Locale.ROOT) : "§2§lNORMAL DISC GOLF";
        List<String> standings = new ArrayList<>();
        if (aceRace != null) {
            for (var entry : aceStandings) standings.add("§7" + entry.line());
        } else if (closest != null) {
            for (var entry : closestStandings) standings.add("§7" + entry.line());
        } else if (match != null) {
            for (UUID side : List.of(match.score().first(), match.score().second()))
                standings.add("§7" + match.playerName(side) + ": §f" + match.score().wins(side));
            standings.add("§7Halved: §f" + match.score().halved());
        } else round.getScoreCards().entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().getTotalScore())).limit(8).forEach(e -> {
            UUID owner = e.getKey().getTeamMembers().stream().findFirst().orElse(null);
            Player member = owner == null ? null : Bukkit.getPlayer(owner);
            String score = elimination == null ? String.valueOf(e.getValue().getTotalScore())
                    : elimination.isSafe(e.getKey()) ? "Safe"
                    : String.valueOf(e.getValue().getHoleScores().getOrDefault(round.getCurrentHole(), e.getValue().getCurrentStrokes()));
            standings.add("§7" + (member == null ? "Team" : member.getName()) + ": §f" + score);
        });
        for (var team : round.getTeams()) for (UUID id : team.getTeamMembers()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            boolean textured = MenuSkin.available(player);
            Sidebar sidebar = boards.computeIfAbsent(id, ignored -> new Sidebar(player, title, textured));
            var card = round.getScoreCardByTeam(team);
            var lie = play.lie(team);
            double distance = lie == null ? 0 : lie.distance(round.getCourse().basket(player.getWorld(), round.getCurrentHole()));
            Integer scored = (match == null && elimination == null && !paired && !(round instanceof DailyRound)) || card == null
                    ? null : card.getHoleScores().get(round.getCurrentHole());
            List<String> lines = new ArrayList<>(List.of(
                    "§aHole " + (round.getCurrentHole() + 1) + " / " + round.getCourse().holes(),
                    (single == null ? "§fPar " + round.getCourse().holePars().get(round.getCurrentHole()) : "§fOne throw")
                            + (textured ? " §f• " : " §7• ") + Math.round(distance) + " blocks"
                            + (round instanceof DailyRound daily ? daily.ghosts().target(id, round.getCurrentHole()) : ""),
                    single != null ? "§fAttempt: " + single.holeResult(team)
                            : "§fStrokes: " + (scored != null ? scored + " (in)" : card == null ? "0" : String.valueOf(card.getCurrentStrokes())),
                    closest != null ? "§6Total: " + closest.total(team).label()
                            : aceRace != null ? "§6Aces: " + card.getHoledOutCount()
                            : elimination != null ? "§6" + elimination.stageLine()
                            : match == null ? "§fScore: " + (card == null ? 0 : card.getTotalScore()) : "§6" + match.scoreLine(),
                    "§eTurn: " + (choosing != null ? "Choosing a lie" : thrower == null ? "Waiting" : thrower.getName()),
                    play.inFlight() ? "§bDisc in flight" : (textured ? "§fTime: " : "§7Time: ") + seconds + "s",
                    textured ? (match == null ? "§aStandings" : "§aHole wins")
                            : (match == null ? "§8──── Standings ────" : "§8──── Hole wins ────")));
            for (String line : standings) lines.add(textured ? "§f" + ChatColor.stripColor(line) : line);
            sidebar.update(lines, title, textured);
            var drawn = play.rouletteDisc(id);
            String preview = RoundPresentation.previewGuidance(player);
            String hint = preview != null ? preview
                    : play.spectating().viewing(id) ? "§eSneak or use /ndg skip to exit the camera"
                    : play.spectating().replaying() ? "§bInstant replay • Next turn shortly"
                    : play.inFlight() ? "§bDisc in flight"
                    : single != null && single.hasFinishedHole(team) ? "§7Attempt complete • Waiting for the next hole"
                    : single != null && id.equals(active) && !play.inFlight() ? "§eYour one throw • Right-click to aim"
                    : elimination != null && elimination.isSafe(team) ? "§aSafe this hole §7• Waiting for the shootout"
                    : choosing != null ? (team == choosing ? "§eChoose a lie • /ndg lie 1 or /ndg lie 2" : "§7Opponents are choosing a lie")
                    : id.equals(active) && play.usesOneDisc() && play.lockedDisc(id) == null
                        ? "§aYour turn • Choose your one disc in /ndg bag"
                    : drawn != null && !play.inFlight() ? "§d" + drawn.displayName() + " §7• §aRight-click to aim"
                    : id.equals(active) ? "§aYour turn • Choose a disc and right-click • Swap hands to change technique"
                    : "§7Waiting for " + (thrower == null ? "next turn" : thrower.getName());
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(hint));
        }
    }
    public void remove(UUID id) {
        Sidebar sidebar = boards.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (player != null && sidebar != null && player.getScoreboard() == sidebar.board) player.setScoreboard(sidebar.original);
    }
    public void dispose() { for (UUID id : new ArrayList<>(boards.keySet())) remove(id); }
}
