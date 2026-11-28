package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.impl.util.Bounds;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Short, skippable flyover. The player's location is the camera; every exit restores state. */
public final class RoundPresentation {
    private static final Map<UUID, Preview> PREVIEWS = new HashMap<>();
    private record Preview(String roundId, Player player, Location destination, GameMode mode, boolean allowFlight,
                           boolean flying, String guidance, BukkitTask task) {}
    private RoundPresentation() {}
    public static boolean isPreviewing(Player player) { return PREVIEWS.containsKey(player.getUniqueId()); }
    public static boolean isPreviewing(GameRound round) { return PREVIEWS.values().stream().anyMatch(p -> p.roundId().equals(round.getId())); }
    static String previewGuidance(Player player) {
        Preview preview = PREVIEWS.get(player.getUniqueId());
        return preview == null ? null : preview.guidance();
    }
    public static void teleportToCurrentTee(Plugin plugin, Player player, GameRound round, boolean preview) {
        cancel(player);
        if (round.isOver() || !round.containsPlayer(player.getUniqueId())) return;
        World world = ((RoundImpl) round).world();
        if (world == null) return;
        if (preview && !((RoundImpl) round).getCourse().isCustom() && plugin.getConfig().getBoolean("preview.enabled", true))
            playHolePreview(plugin, player, round);
        else player.teleport(teePosition(round, player, world));
    }
    public static Location getGroundedCurrentTee(GameRound round, World world) {
        return ((CourseImpl)round.getCourse()).tee(world, round.getCurrentHole());
    }
    public static void teleportTeamToLie(Player thrower, GameRound round, Location lie) {
        Team team = round.getTeams().stream().filter(t -> t.contains(thrower.getUniqueId())).findFirst().orElse(null);
        if (team == null) return;
        teleportTeamToLie(team, round, lie);
    }
    public static void teleportTeamToLie(Team team, GameRound round, Location lie) {
        for (UUID id : team.getTeamMembers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                Location destination = watchingPosition((RoundImpl)round, player, lie);
                destination.setYaw(player.getLocation().getYaw());
                destination.setPitch(player.getLocation().getPitch());
                player.teleport(destination);
            }
        }
    }
    public static void playHolePreview(Plugin plugin, Player player, GameRound round) {
        cancel(player);
        if (round.isOver() || !player.isOnline()) return;
        var impl = (RoundImpl)round;
        CourseImpl course = impl.getCourse();
        int hole = round.getCurrentHole();
        World world = impl.world();
        if (world == null || course.isCustom()) return;
        Location destination = teePosition(round, player, world);
        HoleFlyover flyover = impl.play().flyover(world, hole);
        int ticks = Bounds.clamp(plugin.getConfig().getInt("preview.seconds", 6), 2, 10) * 20;
        GameMode mode = player.getGameMode();
        boolean allowFlight = player.getAllowFlight(), flying = player.isFlying();
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(flyover.frame(0));
        player.sendTitle("§aHole " + (hole + 1) + " • " + course.grid().holeName(hole),
                "§f" + flyover.subtitle(), 5, Math.min(60, ticks - 15), 10);
        BukkitTask task = new BukkitRunnable() {
            private int elapsed;
            public void run() {
                if (!player.isOnline() || round.isOver() || !round.containsPlayer(player.getUniqueId()) || round.getCurrentHole() != hole || elapsed >= ticks) {
                    RoundPresentation.cancel(player); return;
                }
                double progress = elapsed / (double)(ticks - 1);
                Location camera = flyover.frame(progress);
                player.teleport(camera);
                if (elapsed++ % 8 == 0) flyover.markRoute(player, camera, progress);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        PREVIEWS.put(player.getUniqueId(), new Preview(round.getId(), player, destination, mode, allowFlight, flying,
                flyover.guidance(), task));
    }
    public static void cancel(Player player) {
        Preview preview = PREVIEWS.remove(player.getUniqueId());
        if (preview == null) return;
        preview.task().cancel();
        player.resetTitle();
        if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
        player.setGameMode(preview.mode());
        player.setAllowFlight(preview.allowFlight());
        player.setFlying(preview.allowFlight() && preview.flying());
        player.teleport(preview.destination());
    }
    public static Location teePosition(GameRound round, Player player, World world) {
        var impl = (RoundImpl)round;
        Location tee = impl.getCourse().tee(world, round.getCurrentHole());
        UUID active = impl.play().thrower(impl.play().currentTeam());
        if (player.getUniqueId().equals(active)) return tee;
        Location position = watchingPosition(impl, player, tee);
        if (!impl.getCourse().isCustom() && impl.getCourse().grid().woodlandHole(round.getCurrentHole()) != null) {
            position.setYaw(tee.getYaw());
            position.setPitch(tee.getPitch());
        }
        return position;
    }

    private static Location watchingPosition(RoundImpl round, Player player, Location lie) {
        List<UUID> ids = round.getTeams().stream().flatMap(t -> t.getTeamMembers().stream()).toList();
        int index = Math.max(0, ids.indexOf(player.getUniqueId()));
        var direction = round.getCourse().basket(lie.getWorld(),round.getCurrentHole()).toVector().subtract(lie.toVector()).setY(0);
        if (direction.lengthSquared() < .001) direction.setZ(1);
        direction.normalize();
        var side = new org.bukkit.util.Vector(-direction.getZ(),0,direction.getX());
        Location position = lie.clone().subtract(direction.multiply(3.5 + (index/3)*3)).add(side.multiply((index%3-1)*2.5));
        if (round.getCourse().isCustom()) {
            position = round.getCourse().safeLie(position, lie);
            return position.setDirection(round.getCourse().basket(lie.getWorld(), round.getCurrentHole()).toVector().subtract(position.toVector()));
        }
        int x = position.getBlockX(), z = position.getBlockZ();
        position.setY(round.getCourse().grid().surfaceHeight(x,z)+1);
        var grid = round.getCourse().grid();
        if (grid.surfaceType(x, z).isHazard()) return lie.clone();
        if (!position.getBlock().isPassable() || !position.clone().add(0,1,0).getBlock().isPassable()) {
            position = lie.clone().add(0,0,-4);
            if (grid.surfaceType(position.getBlockX(), position.getBlockZ()).isHazard()) return lie.clone();
            position.setY(round.getCourse().grid().surfaceHeight(position.getBlockX(),position.getBlockZ())+1);
        }
        position.setDirection(round.getCourse().basket(lie.getWorld(),round.getCurrentHole()).toVector().subtract(position.toVector()));
        return position;
    }

    /** Clear the throwing lane and return the next thrower to the server-owned lie. */
    public static void prepareTurn(RoundImpl round) {
        UUID active = round.play().thrower(round.play().currentTeam());
        if (active == null || isPreviewing(round)) return;
        Location lie = round.play().lie(round.play().currentTeam());
        if (lie == null) return;
        for (Team team : round.getTeams()) for (UUID id : team.getTeamMembers()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            if (!id.equals(active) && player.getWorld()==lie.getWorld() && player.getLocation().distanceSquared(lie)<9)
                player.teleport(watchingPosition(round,player,lie));
        }
        Player thrower = Bukkit.getPlayer(active);
        if (thrower != null) {
            Location destination = lie.clone();
            var tee = round.getCourse().tee(lie.getWorld(), round.getCurrentHole());
            if (lie.distanceSquared(tee) < .01) {
                destination.setYaw(tee.getYaw());
                destination.setPitch(tee.getPitch());
            } else {
                destination.setDirection(round.getCourse().basket(lie.getWorld(),round.getCurrentHole()).toVector().subtract(lie.toVector()));
            }
            thrower.teleport(destination);
        }
    }

    public static void cancelRound(GameRound round) {
        for (Preview preview : new ArrayList<>(PREVIEWS.values()))
            if (preview.roundId().equals(round.getId())) cancel(preview.player());
    }
    public static void cancelAll() { for (Preview preview : new ArrayList<>(PREVIEWS.values())) cancel(preview.player()); }
}
