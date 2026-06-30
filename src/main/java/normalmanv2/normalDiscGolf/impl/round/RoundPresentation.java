package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public final class RoundPresentation {

    private static final int PREVIEW_STEPS = 7;
    private static final long PREVIEW_STEP_TICKS = 14L;
    private static final double TEE_Y_OFFSET = 1.0;
    private static final double PREVIEW_Y_OFFSET = 10.0;

    private RoundPresentation() {
    }

    public static void teleportToCurrentTee(Plugin plugin, Player player, GameRound round, boolean showPreview) {
        World world = Bukkit.getWorld(round.getId());
        if (world == null) {
            world = player.getWorld();
        }

        Location teeLocation = getGroundedCurrentTee(round, world);
        player.teleport(teeLocation);

        if (showPreview && plugin != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> playHolePreview(plugin, player, round), 10L);
        }
    }

    public static Location getGroundedCurrentTee(GameRound round, World world) {
        Location teeLocation = round.getNextTeeLocation(world);
        return getGroundedLocation(world, teeLocation, TEE_Y_OFFSET);
    }

    public static void playHolePreview(Plugin plugin, Player player, GameRound round) {
        if (plugin == null || player == null || !player.isOnline() || round.isOver()) {
            return;
        }

        World world = Bukkit.getWorld(round.getId());
        if (world == null) {
            world = player.getWorld();
        }

        int hole = round.getCurrentHole();
        CourseGrid.GridPoint pinPoint = round.getCourse().holeLocations().get(hole);
        if (pinPoint == null) {
            return;
        }

        Location returnLocation = getGroundedCurrentTee(round, world);
        Location pinLocation = getGroundedLocation(world, round.getCourse().toLocation(world, pinPoint), TEE_Y_OFFSET);
        List<Location> previewPath = buildPreviewPath(round, world, hole, pinLocation);

        if (previewPath.isEmpty()) {
            return;
        }

        GameMode originalGameMode = player.getGameMode();
        boolean originalAllowFlight = player.getAllowFlight();
        boolean originalFlying = player.isFlying();

        player.sendTitle(
                ChatColor.GREEN + "Hole " + (hole + 1),
                ChatColor.WHITE + "Previewing the fairway",
                10,
                45,
                10
        );
        player.setGameMode(GameMode.SPECTATOR);

        for (int i = 0; i < previewPath.size(); i++) {
            int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || round.isOver()) {
                    return;
                }

                Location target = previewPath.get(Math.min(index + 1, previewPath.size() - 1));
                player.teleport(faceLocation(previewPath.get(index).clone(), target));
            }, i * PREVIEW_STEP_TICKS);
        }

        long restoreDelay = previewPath.size() * PREVIEW_STEP_TICKS + 8L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            player.setGameMode(originalGameMode);
            player.setAllowFlight(originalAllowFlight);
            if (originalAllowFlight) {
                player.setFlying(originalFlying);
            }
            player.teleport(faceLocation(returnLocation.clone(), pinLocation));
            player.sendMessage(ChatColor.GRAY + "Throw toward the red pin marker.");
        }, restoreDelay);
    }

    private static Location getGroundedLocation(World world, Location baseLocation, double yOffset) {
        int x = baseLocation.getBlockX();
        int z = baseLocation.getBlockZ();
        int y = world.getHighestBlockYAt(x, z);
        return new Location(
                world,
                x + 0.5,
                y + yOffset,
                z + 0.5,
                baseLocation.getYaw(),
                baseLocation.getPitch()
        );
    }

    private static List<Location> buildPreviewPath(GameRound round, World world, int hole, Location pinLocation) {
        List<CourseGrid.GridPoint> routePoints = getRoutePoints(round, hole);
        List<Location> previewPath = new ArrayList<>();

        if (routePoints.isEmpty()) {
            Location tee = getGroundedCurrentTee(round, world).add(0, PREVIEW_Y_OFFSET, 0);
            previewPath.add(tee);
            previewPath.add(pinLocation.clone().add(0, PREVIEW_Y_OFFSET, 0));
            return previewPath;
        }

        int lastIndex = routePoints.size() - 1;
        int steps = Math.min(PREVIEW_STEPS, routePoints.size());

        for (int i = 0; i < steps; i++) {
            int routeIndex = steps == 1 ? 0 : (int) Math.round(i * (lastIndex / (double) (steps - 1)));
            CourseGrid.GridPoint point = routePoints.get(routeIndex);
            Location routeLocation = round.getCourse()
                    .toLocation(world, point)
                    .add(Constants.DEFAULT_TILE_SIZE / 2.0, 0, Constants.DEFAULT_TILE_SIZE / 2.0);
            previewPath.add(getGroundedLocation(world, routeLocation, PREVIEW_Y_OFFSET));
        }

        previewPath.add(pinLocation.clone().add(0, PREVIEW_Y_OFFSET, 0));
        return previewPath;
    }

    private static List<CourseGrid.GridPoint> getRoutePoints(GameRound round, int hole) {
        if (round.getCourse() instanceof CourseImpl course) {
            return course.grid().getHoleRoutePoints().getOrDefault(hole, List.of());
        }

        return List.of();
    }

    private static Location faceLocation(Location from, Location target) {
        from.setDirection(target.toVector().subtract(from.toVector()));
        return from;
    }
}
