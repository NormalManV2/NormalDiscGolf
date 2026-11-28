package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.DiscFlightResolver;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import normalmanv2.normalDiscGolf.impl.round.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class RoundScorecardMenu extends InventoryGUI {
    private record Hole(Material icon, String title, List<String> detail) {}
    private record Card(List<String> header, List<Hole> holes, List<String> standings, boolean match,
                        RoundGhosts.Controls ghost) {}

    private final RoundImpl round;
    private Card shown;
    private BukkitTask refresh;

    private RoundScorecardMenu(RoundImpl round) {
        super(MenuSkin.SCORECARD);
        this.round = round;
    }

    public static void open(Player player, RoundImpl round) {
        NDGManager.getInstance().getGuiManager().openGUI(new RoundScorecardMenu(round), player);
    }

    @Override public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        redraw(player);
        var plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
        refresh = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (DiscFlightResolver.findActiveRound(player) != round) player.closeInventory();
            else redraw(player);
        }, 20, 20);
    }

    @Override public void onClose(InventoryCloseEvent event) {
        if (refresh != null) refresh.cancel();
    }

    private void redraw(Player player) {
        UUID viewer = player.getUniqueId();
        Team team = round.play().team(viewer);
        ScoreCard score = team == null ? null : round.getScoreCardByTeam(team);
        if (score == null) return;
        var format = RoundFormat.of(round);
        String mode = format == null ? "Disc Golf" : format.label();
        var header = List.of(mode + " · Hole " + (round.getCurrentHole() + 1) + " / " + round.getCourse().holes(),
                total(team, score), "Holes finished: " + finishedHoles(team, score) + " / " + round.getCourse().holes());
        List<Hole> holes = new ArrayList<>();
        var ghosts = round.play().ghosts();
        for (int hole = 0; hole < round.getCourse().holes(); hole++)
            holes.add(hole(team, score, hole, ghosts == null ? null : ghosts.bestOnHole(viewer, hole)));
        Card card = new Card(header, holes, standings(), round instanceof MatchPlayRound,
                ghosts == null ? null : ghosts.controls(viewer));
        if (card.equals(shown)) return;
        shown = card;

        var menu = getInventory();
        menu.clear();
        menu.setItem(4, ShopItems.icon(Material.WRITTEN_BOOK, ChatColor.AQUA + "Your Scorecard",
                header.stream().map(line -> ChatColor.GRAY + line).toArray(String[]::new)));
        for (int i = 0; i < holes.size(); i++) {
            Hole hole = holes.get(i);
            var item = ShopItems.icon(hole.icon(), hole.title(), hole.detail().toArray(String[]::new));
            item.setAmount(i + 1);
            menu.setItem(9 + i, item);
        }
        if (card.standings().size() > 1)
            menu.setItem(31, ShopItems.icon(Material.BOOK, ChatColor.GOLD + (card.match() ? "Hole Wins" : "Standings"),
                    card.standings().stream().map(line -> ChatColor.GRAY + line).toArray(String[]::new)));
        menu.setItem(45, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Return to Round"));
        menu.setItem(49, ShopItems.icon(Material.PAPER, ChatColor.AQUA + "Show in Chat"));
        if (card.ghost() != null) drawGhostControls(card.ghost());
        applyTextures();
    }

    private void drawGhostControls(RoundGhosts.Controls ghost) {
        var menu = getInventory();
        if (!ghost.recorded()) {
            menu.setItem(53, ShopItems.icon(Material.CLOCK, ChatColor.GRAY + "No Personal Ghost Yet",
                    ChatColor.GRAY + "Hole out on every hole to record your best."));
            return;
        }
        menu.setItem(47, ShopItems.icon(Material.SPYGLASS, ChatColor.AQUA + "Replay Ghost",
                ChatColor.GRAY + "Watch your best throws on this hole"));
        String toggle = ghost.visible() ? "Hide Ghost Paths" : ghost.playing() ? "Stop Ghost Replay" : "Show Ghost Paths";
        menu.setItem(51, ShopItems.icon(Material.AMETHYST_SHARD, ChatColor.AQUA + toggle));
        menu.setItem(53, ShopItems.icon(Material.CLOCK, ChatColor.AQUA + "Compare " + ghost.label() + " Ghost"));
    }

    private int finishedHoles(Team team, ScoreCard card) {
        if (!(round instanceof SingleThrowRound single)) return card.getHoleScores().size();
        return round.getCurrentHole() + (single.hasFinishedHole(team) ? 1 : 0);
    }

    private String total(Team team, ScoreCard score) {
        if (round instanceof MatchPlayRound match) return match.scoreLine();
        if (round instanceof EliminationRound elimination) return elimination.stageLine();
        if (round instanceof AceRaceRound) return AceRaceRound.aceCount(score.getHoledOutCount());
        if (round instanceof ClosestToPinRound closest) return closest.total(team).label();
        return relative(score.getTotalScore()) + " · " + MatchPlayRound.strokeCount(score.getTotalStrokes());
    }

    private Hole hole(Team team, ScoreCard card, int hole, Integer ghostTarget) {
        boolean current = hole == round.getCurrentHole();
        String name = "Hole " + (hole + 1) + (current ? " · Current" : "");
        Material color = current ? Material.YELLOW_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        var details = new ArrayList<String>();
        if (round instanceof SingleThrowRound single) {
            String result = single.holeResult(team, hole);
            if (result.equals("Ace")) color = Material.GREEN_STAINED_GLASS_PANE;
            else if (result.equals("Miss") || result.equals("No score")) color = Material.RED_STAINED_GLASS_PANE;
            else if (!result.equals("Pending")) color = Material.GREEN_STAINED_GLASS_PANE;
            details.add(ChatColor.WHITE + result);
        } else {
            int par = round.getCourse().holePars().get(hole);
            details.add(ChatColor.GRAY + "Par " + par);
            Integer strokes = card.getHoleScores().get(hole);
            if (strokes == null) {
                details.add(ChatColor.WHITE + (current ? MatchPlayRound.strokeCount(card.getCurrentStrokes()) + " so far" : "Awaiting score"));
            } else {
                int difference = strokes - par;
                color = difference < 0 ? Material.GREEN_STAINED_GLASS_PANE
                        : difference > 0 ? Material.RED_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE;
                details.add(ChatColor.WHITE + MatchPlayRound.strokeCount(strokes) + " · " + relative(difference));
                details.add(ChatColor.GRAY + (card.getBaskets().containsKey(hole) ? "Basket" : "Recorded without a basket"));
            }
            if (round instanceof MatchPlayRound match) match.score().holes().stream()
                    .filter(result -> result.index() == hole).findFirst()
                    .ifPresent(result -> details.add(ChatColor.AQUA + match.describe(result)));
        }
        if (ghostTarget != null) details.add(ChatColor.AQUA + "Ghost target: " + MatchPlayRound.strokeCount(ghostTarget));
        return new Hole(color, (current ? ChatColor.GOLD : ChatColor.AQUA) + name, details);
    }

    private List<String> standings() {
        if (round instanceof AceRaceRound ace) return ace.standings().stream().map(AceRaceRound.Standing::line).toList();
        if (round instanceof ClosestToPinRound closest)
            return closest.standings().stream().map(ClosestToPinRound.Standing::line).toList();
        if (round instanceof MatchPlayRound match) return List.of(match.scoreLine(), match.resultsLine());
        return round.getScoreCards().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<Team, ScoreCard> row) -> row.getValue().getTotalScore())
                        .thenComparingInt(row -> row.getValue().getTotalStrokes()).thenComparing(row -> teamName(row.getKey())))
                .map(row -> teamName(row.getKey()) + " · " + (round instanceof EliminationRound elimination
                        ? elimination.isSafe(row.getKey()) ? "Safe" : MatchPlayRound.strokeCount(row.getValue()
                        .getHoleScores().getOrDefault(round.getCurrentHole(), row.getValue().getCurrentStrokes()))
                        : relative(row.getValue().getTotalScore()) + " · " + MatchPlayRound.strokeCount(row.getValue().getTotalStrokes())))
                .toList();
    }

    private static String teamName(Team team) {
        return team.getTeamMembers().stream().map(RoundScorecardMenu::playerName).collect(Collectors.joining(", "));
    }

    private static String playerName(UUID id) {
        Player player = Bukkit.getPlayer(id);
        String name = player == null ? Bukkit.getOfflinePlayer(id).getName() : player.getName();
        return name == null ? id.toString() : name;
    }

    private static String relative(int score) { return score == 0 ? "Even par" : score > 0 ? "+" + score : Integer.toString(score); }

    @Override public void onClick(InventoryClickEvent event) {
        if (!event.isLeftClick() || !(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        var ghosts = round.play().ghosts();
        boolean ghostControl = slot == 47 || slot == 51 || slot == 53;
        if (slot != 45 && slot != 49 && !(ghostControl && ghosts != null)) return;
        ShopItems.navigate(player, getInventory(), () -> {
            if (DiscFlightResolver.findActiveRound(player) != round) {
                player.closeInventory();
                return;
            }
            if (ghostControl) {
                var control = ghosts.controls(player.getUniqueId());
                if (!control.recorded()) return;
                if (slot == 51) {
                    ghosts.command(player, control.visible() || control.playing() ? "off" : "on");
                    redraw(player);
                    return;
                }
                player.closeInventory();
                ghosts.command(player, slot == 47 ? "play" : "compare");
                return;
            }
            player.closeInventory();
            if (slot == 49) player.performCommand("ndg score text");
        });
    }
}
