package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseTerrain;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

final class HoleFlyover {
    private static final Particle.DustOptions FAIRWAY = new Particle.DustOptions(Color.fromRGB(255, 205, 95), 1.3f);
    private static final Particle.DustOptions WATER = new Particle.DustOptions(Color.fromRGB(74, 211, 255), 1.4f);
    private static final Particle.DustOptions LAVA = new Particle.DustOptions(Color.fromRGB(255, 108, 50), 1.4f);
    private static final Particle.DustOptions VOID = new Particle.DustOptions(Color.fromRGB(197, 125, 255), 1.4f);
    private static final Particle.DustOptions BASKET = new Particle.DustOptions(Color.fromRGB(236, 255, 191), 1.55f);

    private record RoutePoint(double x, double z, double sidewaysX, double sidewaysZ, double routeHeight) {}
    private record Hazard(Location marker, Particle.DustOptions color, String caption) {}

    private final HolePreviewPath path;
    private final boolean woodland;
    private final List<Location> fairway;
    private final Hazard hazard;
    private final Location basket;
    private final String subtitle;

    HoleFlyover(CourseImpl course, World world, int hole) {
        CourseGrid grid = course.grid();
        Location tee = course.tee(world, hole);
        basket = course.basket(world, hole);
        var route = grid.centerline(hole);
        var featured = grid.woodlandHole(hole);
        woodland = featured != null;
        subtitle = woodland ? featured.hint(route) : grid.terrain().label() + " • Par " + course.holePars().get(hole);
        var waypoints = new ArrayList<Location>();
        waypoints.add(tee);
        for (int i = 1; i < route.size() - 1; i++) {
            var point = route.get(i);
            double height = grid.surfaceType(point.x(), point.z()) == TileTypes.OUT_OF_BOUNDS
                    ? tee.getY() + (basket.getY() - tee.getY()) * i / (route.size() - 1)
                    : grid.surfaceHeight(point.x(), point.z()) + 1;
            waypoints.add(new Location(world, point.x() + .5, height, point.z() + .5));
        }
        waypoints.add(basket);
        path = new HolePreviewPath(waypoints);
        var samples = samples(waypoints);
        fairway = samples.stream().filter(point -> !grid.surfaceType(floor(point.x()), floor(point.z())).isHazard())
                .map(point -> new Location(world, point.x(), grid.surfaceHeight(floor(point.x()), floor(point.z())) + 1.6,
                        point.z())).toList();
        hazard = nearestHazard(grid, world, hole, samples);
    }

    String subtitle() { return subtitle; }

    String guidance() {
        return hazard == null ? "§6Gold marks the fairway §7• Sneak to skip" : hazard.caption() + " §7• Sneak to skip";
    }

    Location frame(double progress) {
        return path.previewFrame(progress, woodland, hazard == null ? null : hazard.marker());
    }

    void markRoute(Player player, Location camera, double progress) {
        for (Location marker : fairway) if (marker.distanceSquared(camera) <= 32 * 32) dot(player, marker, FAIRWAY);
        if (hazard != null && hazard.marker().distanceSquared(camera) <= 38 * 38 && progress < .9) {
            Location marker = hazard.marker();
            ring(player, marker, 2.1, hazard.color());
            dot(player, marker.clone().add(0, 1.5, 0), hazard.color());
        }
        if (progress >= .58 && basket.distanceSquared(camera) <= 32 * 32) {
            ring(player, basket.clone().add(0, 2.4, 0), 1.35, BASKET);
            for (int i = 1; i <= 4; i++) dot(player, basket.clone().add(0, 2.65 + i * .55, 0), BASKET);
        }
    }

    private static List<RoutePoint> samples(List<Location> waypoints) {
        List<RoutePoint> result = new ArrayList<>();
        for (int i = 1; i < waypoints.size(); i++) {
            var start = waypoints.get(i - 1);
            var end = waypoints.get(i);
            double dx = end.getX() - start.getX(), dz = end.getZ() - start.getZ();
            double length = Math.hypot(dx, dz);
            int steps = (int)Math.ceil(length / 3);
            for (int step = 0; step < steps; step++) {
                double t = step / (double)steps;
                result.add(new RoutePoint(start.getX() + dx * t, start.getZ() + dz * t, -dz / length, dx / length,
                        start.getY() + (end.getY() - start.getY()) * t));
            }
        }
        return result;
    }

    /** Inspect the actual generated surface, giving a crossing priority over a feature beside the route. */
    private static Hazard nearestHazard(CourseGrid grid, World world, int hole, List<RoutePoint> samples) {
        int reach = grid.terrain() == CourseTerrain.ISLAND || grid.terrain() == CourseTerrain.SKY ? 36 : 24;
        for (int offset = 0; offset <= reach; offset += 2) {
            for (RoutePoint point : samples) {
                int leftX = floor(point.x() + point.sidewaysX() * offset);
                int leftZ = floor(point.z() + point.sidewaysZ() * offset);
                Hazard left = hazardAt(grid, world, hole, leftX, leftZ, point.routeHeight(), offset == 0);
                if (left != null) return left;
                if (offset == 0) continue;
                int rightX = floor(point.x() - point.sidewaysX() * offset);
                int rightZ = floor(point.z() - point.sidewaysZ() * offset);
                Hazard right = hazardAt(grid, world, hole, rightX, rightZ, point.routeHeight(), false);
                if (right != null) return right;
            }
        }
        return null;
    }

    private static Hazard hazardAt(CourseGrid grid, World world, int hole, int x, int z, double routeHeight, boolean crossing) {
        if (!grid.inHoleCell(hole, x, z)) return null;
        TileTypes type = grid.surfaceType(x, z);
        if (!type.isHazard() || type == TileTypes.OUT_OF_BOUNDS && grid.terrain() != CourseTerrain.SKY) return null;
        double height = type == TileTypes.OUT_OF_BOUNDS ? routeHeight - 1.5 : grid.surfaceHeight(x, z) + 1.1;
        var marker = new Location(world, x + .5, height, z + .5);
        return switch (type) {
            case WATER -> new Hazard(marker, WATER, crossing ? "§bWater carry" : "§bWater beside the fairway");
            case LAVA -> new Hazard(marker, LAVA, crossing ? "§cLava carry" : "§cLava beside the fairway");
            case OUT_OF_BOUNDS -> new Hazard(marker, VOID, crossing ? "§dVoid carry" : "§dVoid beside the fairway");
            default -> throw new IllegalStateException("Unexpected hole hazard " + type);
        };
    }

    private static int floor(double value) { return (int)Math.floor(value); }

    private static void ring(Player player, Location center, double radius, Particle.DustOptions color) {
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.TAU / 8;
            dot(player, center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), color);
        }
    }

    private static void dot(Player player, Location point, Particle.DustOptions color) {
        player.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, color);
    }
}
