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
        for (var team : round.getTeams()) for (UUID id : team.getTeamMembers()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            Scoreboard board = boards.computeIfAbsent(id, ignored -> {
                originals.put(id, player.getScoreboard());
                Scoreboard created = Bukkit.getScoreboardManager().getNewScoreboard();
                created.registerNewObjective("ndg", Criteria.DUMMY, "§2§lNORMAL DISC GOLF").setDisplaySlot(DisplaySlot.SIDEBAR);
                player.setScoreboard(created);
                return created;
            });
            for (String entry : new HashSet<>(board.getEntries())) board.resetScores(entry);
            var card = round.getScoreCardByTeam(team);
            var lie = play.lie(team);
            double distance = lie == null ? 0 : lie.distance(round.getCourse().basket(player.getWorld(), round.getCurrentHole()));
            List<String> lines = new ArrayList<>(List.of(
                    "§aHole " + (round.getCurrentHole() + 1) + " / " + round.getCourse().holes(),
                    "§fPar " + round.getCourse().holePars().get(round.getCurrentHole()) + " §7• " + Math.round(distance) + " blocks",
                    "§fStrokes: " + (card == null ? 0 : card.getCurrentStrokes()),
                    "§fScore: " + (card == null ? 0 : card.getTotalScore()),
                    "§eTurn: " + (thrower == null ? "Waiting" : thrower.getName()),
                    play.inFlight() ? "§bDisc in flight" : "§7Time: " + seconds + "s",
                    "§8──── Standings ────"));
            round.getScoreCards().entrySet().stream().sorted(Comparator.comparingInt(e -> e.getValue().getTotalScore())).limit(8).forEach(e -> {
                UUID owner = e.getKey().getTeamMembers().stream().findFirst().orElse(null);
                Player member = owner == null ? null : Bukkit.getPlayer(owner);
                lines.add("§7" + (member == null ? "Team" : member.getName()) + ": §f" + e.getValue().getTotalScore());
            });
            Objective objective = board.getObjective("ndg");
            for (int i = 0; i < lines.size(); i++) objective.getScore(lines.get(i) + "§" + Integer.toHexString(i)).setScore(lines.size() - i);
            String hint = RoundPresentation.isPreviewing(player) ? "§eSneak to skip the preview"
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
