package normalmanv2.normalDiscGolf.impl.round;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import java.util.*;

public final class RoundHud {
    private final RoundImpl round;
    private final Map<UUID, Scoreboard> originals = new HashMap<>();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
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
        for (var team : round.getTeams()) for (UUID id : team.getTeamMembers()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            Scoreboard board = boards.computeIfAbsent(id, ignored -> {
                originals.put(id, player.getScoreboard());
                Scoreboard created = Bukkit.getScoreboardManager().getNewScoreboard();
                created.registerNewObjective("ndg", Criteria.DUMMY, paired || play.usesOneDisc() || play.usesRoulette()
                        || elimination != null || single != null || round instanceof ArcadeRound || round instanceof CompetitionRound
                        ? "§2§l" + RoundFormat.of(round).label().toUpperCase(Locale.ROOT) : "§2§lNORMAL DISC GOLF")
                        .setDisplaySlot(DisplaySlot.SIDEBAR);
                player.setScoreboard(created);
                return created;
            });
            for (String entry : new HashSet<>(board.getEntries())) board.resetScores(entry);
            var card = round.getScoreCardByTeam(team);
            var lie = play.lie(team);
            double distance = lie == null ? 0 : lie.distance(round.getCourse().basket(player.getWorld(), round.getCurrentHole()));
            Integer scored = (match == null && elimination == null && !paired) || card == null
                    ? null : card.getHoleScores().get(round.getCurrentHole());
            List<String> lines = new ArrayList<>(List.of(
                    "§aHole " + (round.getCurrentHole() + 1) + " / " + round.getCourse().holes(),
                    (single == null ? "§fPar " + round.getCourse().holePars().get(round.getCurrentHole()) : "§fOne throw")
                            + " §7• " + Math.round(distance) + " blocks",
                    single != null ? "§fAttempt: " + single.holeResult(team)
                            : "§fStrokes: " + (scored != null ? scored + " (in)" : card == null ? "0" : String.valueOf(card.getCurrentStrokes())),
                    closest != null ? "§6Total: " + closest.total(team).label()
                            : aceRace != null ? "§6Aces: " + card.getHoledOutCount()
                            : elimination != null ? "§6" + elimination.stageLine()
                            : match == null ? "§fScore: " + (card == null ? 0 : card.getTotalScore()) : "§6" + match.scoreLine(),
                    "§eTurn: " + (choosing != null ? "Choosing a lie" : thrower == null ? "Waiting" : thrower.getName()),
                    play.inFlight() ? "§bDisc in flight" : "§7Time: " + seconds + "s",
                    match == null ? "§8──── Standings ────" : "§8──── Hole wins ────"));
            if (aceRace != null) {
                for (var entry : aceStandings) lines.add("§7" + entry.line());
            } else if (closest != null) {
                for (var entry : closestStandings) lines.add("§7" + entry.line());
            } else if (match != null) {
                for (UUID side : List.of(match.score().first(), match.score().second()))
                    lines.add("§7" + match.playerName(side) + ": §f" + match.score().wins(side));
                lines.add("§7Halved: §f" + match.score().halved());
            } else round.getScoreCards().entrySet().stream().sorted(Comparator.comparingInt(e -> e.getValue().getTotalScore())).limit(8).forEach(e -> {
                UUID owner = e.getKey().getTeamMembers().stream().findFirst().orElse(null);
                Player member = owner == null ? null : Bukkit.getPlayer(owner);
                String score = elimination == null ? String.valueOf(e.getValue().getTotalScore())
                        : elimination.isSafe(e.getKey()) ? "Safe"
                        : String.valueOf(e.getValue().getHoleScores().getOrDefault(round.getCurrentHole(), e.getValue().getCurrentStrokes()));
                lines.add("§7" + (member == null ? "Team" : member.getName()) + ": §f" + score);
            });
            Objective objective = board.getObjective("ndg");
            for (int i = 0; i < lines.size(); i++) objective.getScore(lines.get(i) + "§" + Integer.toHexString(i)).setScore(lines.size() - i);
            var drawn = play.rouletteDisc(id);
            String hint = RoundPresentation.isPreviewing(player) ? "§eSneak to skip the preview"
                    : play.spectating().viewing(id) ? "§eSneak or use /ndg skip to exit the camera"
                    : play.spectating().replaying() ? "§bInstant replay • Next turn shortly"
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
        Scoreboard original = originals.remove(id);
        Scoreboard board = boards.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (player != null && original != null && player.getScoreboard() == board) player.setScoreboard(original);
    }
    public void dispose() { for (UUID id : new ArrayList<>(originals.keySet())) remove(id); }
}
