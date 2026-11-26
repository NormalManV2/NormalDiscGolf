package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.event.GoalScoreEvent;
import normalmanv2.normalDiscGolf.impl.manager.flight.DiscFlightManager;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.*;

/** Server-owned lies and shot state. Doubles uses alternate shot, including penalties. */
public final class RoundPlayState {
    private final RoundImpl round;
    private final Map<Team, Location> lies = new HashMap<>();
    private final Map<Team, UUID> lastThrower = new HashMap<>();
    private Shot shot;
    private long deadline;
    private int hole = -1;
    private final RoundHud hud;
    public record Shot(UUID player, int hole, Team team, Location origin) {}

    public RoundPlayState(RoundImpl round) { this.round = round; this.hud = new RoundHud(round); }
    private NormalDiscGolfPlugin plugin() { return NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class); }
    public Team team(UUID id) { return round.getTeams().stream().filter(t -> t.contains(id)).findFirst().orElse(null); }
    public Team currentTeam() { return round.getTeams().stream().filter(round::isTurn).findFirst().orElse(null); }
    public UUID thrower(Team team) {
        if (team == null) return null;
        List<UUID> members = new ArrayList<>(team.getTeamMembers());
        if (members.isEmpty()) return null;
        int previous = members.indexOf(lastThrower.get(team));
        return members.get((previous + 1) % members.size());
    }
    public Location lie(Team team) {
        Location location = lies.get(team);
        return location == null ? null : location.clone();
    }
    public boolean inFlight() { return shot != null; }
    public Shot shot() { return shot; }
    public void turnChanged() {
        turnChanged(true);
    }
    private void turnChanged(boolean preparePosition) {
        deadline = System.currentTimeMillis() + Math.max(10, plugin().getConfig().getInt("turn-timeout-seconds", 90)) * 1000L;
        if (preparePosition) RoundPresentation.prepareTurn(round);
        Team team = currentTeam();
        UUID id = thrower(team);
        Player player = id == null ? null : Bukkit.getPlayer(id);
        if (player != null) message("§e" + player.getName() + "'s turn" + (team.getMaximumPlayers() > 1 ? " • Alternate shot" : ""));
    }
    public void startHole() {
        hole = round.getCurrentHole();
        lies.clear();
        lastThrower.clear();
        World world = Bukkit.getWorld(round.getId());
        for (Team team : round.getTeams()) lies.put(team, round.getCourse().tee(world, hole));
        // The lifecycle positions every player (or starts the preview) immediately after this.
        // Avoid multiple conflicting teleports in the same tick at a hole transition.
        turnChanged(false);
    }
    public String validate(Player player) {
        if (round.isOver() || round.getState() != RoundState.START) return "This round is not accepting throws.";
        if (RoundPresentation.isPreviewing(round)) return "Wait for the preview, or use /ndg skip.";
        if (shot != null) return "Wait for the disc to land.";
        Team team = team(player.getUniqueId());
        if (team == null || !round.isTurn(team)) return "It is not your team's turn.";
        if (!player.getUniqueId().equals(thrower(team))) return "Your teammate throws next (alternate shot).";
        Location lie = lie(team);
        if (lie == null || player.getWorld() != lie.getWorld() || player.getLocation().distanceSquared(lie) > 2.25)
            return "Return to your lie with /ndg lie.";
        return null;
    }
    public boolean beginShot(Player player) {
        String error = validate(player);
        if (error != null) { player.sendMessage("§c" + error); return false; }
        Team team = team(player.getUniqueId());
        shot = new Shot(player.getUniqueId(), hole, team, lie(team));
        return true;
    }
    public void failedShot() { shot = null; turnChanged(); }
    public void resolve(Player player, Location landing, boolean goal) {
        Shot completed = shot;
        if (completed == null || !completed.player().equals(player.getUniqueId()) || completed.hole() != round.getCurrentHole() || round.isOver()) return;
        shot = null;
        lastThrower.put(completed.team(), completed.player());
        ScoreCard card = round.getScoreCardByTeam(completed.team());
        if (goal) {
            Bukkit.getPluginManager().callEvent(new GoalScoreEvent(player, completed.hole(), round));
        } else {
            var type = round.getCourse().grid().getTileTypeAtLocation(landing);
            boolean hazard = type == normalmanv2.normalDiscGolf.impl.course.tile.TileTypes.WATER
                    || type == normalmanv2.normalDiscGolf.impl.course.tile.TileTypes.OUT_OF_BOUNDS;
            Location next = hazard ? completed.origin() : safeLie(landing, completed.origin());
            if (hazard) { card.addPenalty(1); message("§cWater / out of bounds: one penalty stroke. Replay the previous lie."); }
            lies.put(completed.team(), next);
            RoundPresentation.teleportTeamToLie(player, round, next);
            if (card.getCurrentStrokes() >= maxStrokes()) finishHole(completed.team(), "Stroke limit");
        }
        round.nextTurn();
    }
    private Location safeLie(Location landing, Location fallback) {
        World world = landing.getWorld();
        int x = landing.getBlockX(), z = landing.getBlockZ();
        // Search close to the collision for a clear standing position, never on top of a tree.

        for (int radius = 0; radius <= 3; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            int y = round.getCourse().grid().surfaceHeight(x + dx, z + dz) + 1;
            if (world.getBlockAt(x + dx, y - 1, z + dz).getType().isSolid()
                    && world.getBlockAt(x + dx, y, z + dz).isPassable()
                    && world.getBlockAt(x + dx, y + 1, z + dz).isPassable())
                return new Location(world, x + dx + .5, y, z + dz + .5, landing.getYaw(), 0);
        }
        return fallback.clone();
    }
    public int maxStrokes() { return Math.max(3, plugin().getConfig().getInt("max-strokes-per-hole", 12)); }
    private void finishHole(Team team, String reason) {
        ScoreCard card = round.getScoreCardByTeam(team);
        if (card == null || card.getHoleScores().containsKey(hole)) return;
        card.recordHoleScore(hole, maxStrokes(), round.getCourse().holePars().get(hole));
        card.resetCurrentStrokes();
        for (UUID id : team.getTeamMembers()) plugin().cancelMeter(id);
        round.setTeamScored(hole, team);
        message("§e" + reason + ": hole recorded at the stroke limit.");
    }
    public void concede(Player player) {
        if (shot != null || RoundPresentation.isPreviewing(round)) { player.sendMessage("§cWait for the shot or preview to finish."); return; }
        Team team = team(player.getUniqueId());
        if (team == null || !round.isTurn(team)) { player.sendMessage("§cConcede on your team's turn."); return; }
        finishHole(team, "Hole conceded");
        round.nextTurn();
    }
    public void leave(UUID id) {
        Team team = team(id);
        if (team == null) return;
        plugin().cancelMeter(id);
        hud.remove(id);
        Team current = currentTeam();
        boolean interrupted = shot != null && shot.player().equals(id);
        if (interrupted) { DiscFlightManager.cleanupPlayer(id); shot = null; }
        team.removePlayer(id);
        if (team.getTeamMembers().isEmpty()) {
            round.removeTeam(team);
            round.getRoundScoreCardManager().removeScoreCard(team);
            lies.remove(team);
            lastThrower.remove(team);
        }
        if (round.getTeams().isEmpty()) {
            NDGManager.getInstance().getRoundHandler().cancelRound(round);
        } else if ((current == team || interrupted) && shot == null) {
            round.nextTurn();
        } else {
            ((normalmanv2.normalDiscGolf.common.round.DefaultRoundTurnManager) round.getRoundTurnManager()).reconcile();
        }
    }
    public void tick() {
        if (round.isOver()) return;
        for (Team team : new ArrayList<>(round.getTeams())) for (UUID id : new ArrayList<>(team.getTeamMembers()))
            if (Bukkit.getPlayer(id) == null) leave(id);
        if (round.isOver()) return;
        if (hole != round.getCurrentHole()) startHole();
        if (RoundPresentation.isPreviewing(round)) turnChangedSilently();
        if (shot == null && System.currentTimeMillis() >= deadline && currentTeam() != null) {
            finishHole(currentTeam(), "Turn timed out");
            round.nextTurn();
        }
        hud.update(Math.max(0, (deadline - System.currentTimeMillis()) / 1000));
    }
    private void turnChangedSilently() {
        deadline = System.currentTimeMillis() + Math.max(10, plugin().getConfig().getInt("turn-timeout-seconds", 90)) * 1000L;
    }
    public void message(String text) {
        for (Team team : round.getTeams()) for (UUID id : team.getTeamMembers()) {
            Player player = Bukkit.getPlayer(id); if (player != null) player.sendMessage(text);
        }
    }
    public void dispose() { for (Team team : round.getTeams()) for (UUID id : team.getTeamMembers()) plugin().cancelMeter(id); shot = null; lies.clear(); lastThrower.clear(); hud.dispose(); }
}
