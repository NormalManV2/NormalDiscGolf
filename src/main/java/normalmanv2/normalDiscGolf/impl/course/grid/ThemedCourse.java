package normalmanv2.normalDiscGolf.impl.course.grid;

import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid.GridPoint;
import normalmanv2.normalDiscGolf.impl.course.grid.WoodlandHole.Landmark;
import normalmanv2.normalDiscGolf.impl.course.grid.WoodlandHole.LandmarkType;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

final class ThemedCourse {
    private static final int CELL = 112;
    private static final int MARGIN = 16;
    private static final int COLUMNS = 3;
    private final CourseGenerationSettings settings;
    private final long seed;
    private final List<Hole> holes;
    private final List<List<TerrainLandmark>> terrainLandmarks;
    private final double phaseX;
    private final double phaseZ;
    private final int waterline;

    record Hole(HoleArchetype archetype, List<GridPoint> route, List<GridPoint> alternative,
                List<GridPoint> guards, List<Pool> pools, int teeY, int basketY, double width, int par,
                WoodlandHole woodlandHole, List<Landmark> landmarks, TerrainShowcase showcase,
                CanyonArch arch, List<SkyIsland> islands) {
        GridPoint tee() { return route.getFirst(); }
        GridPoint basket() { return route.getLast(); }
        double distance(double x, double z) {
            return Math.min(routeDistance(route, x, z), routeDistance(alternative, x, z));
        }
        boolean nearAnchor(double x, double z, double radius) {
            return squared(tee(), x, z) <= radius * radius || squared(basket(), x, z) <= radius * radius;
        }
    }

    record Pool(GridPoint center, double innerRadius, double radius, boolean crossesFairway) {}
    record ArchColumn(int bottom, int top) {}
    private record CanyonArch(GridPoint center, boolean alongX, int span, int spring, int rise) {
        ArchColumn column(int x, int z, int ground) {
            int dx = x - center.x(), dz = z - center.z();
            int along = Math.abs(alongX ? dx : dz), across = Math.abs(alongX ? dz : dx);
            if (along > 2 || across > span + 2) return null;
            double ratio = Math.min(1, across / (double)span);
            int crown = spring + (int)Math.round(rise * Math.sqrt(1 - ratio * ratio));
            int bottom = across >= span - 2 ? ground + 1 : Math.max(ground + 1, crown - 2);
            int top = crown + (along == 2 ? 0 : 1);
            return bottom > top ? null : new ArchColumn(bottom, top);
        }
    }

    private record SkyIsland(GridPoint center, int radius, int depth, int shape) {
        CourseGrid.TerrainColumn column(int x, int z) {
            int dx = x - center.x(), dz = z - center.z();
            double roughness = Math.sin(dx * .85 + dz * .5 + shape) * .65;
            double distance = Math.hypot(dx, dz * 1.15) + roughness;
            if (distance >= radius) return null;
            int height = center.y() - (int)Math.max(0, distance / 2.8);
            int thickness = 2 + (int)(depth * Math.pow(1 - Math.max(0, distance / radius), .85));
            Material top = distance < radius * .53 ? Material.MOSS_BLOCK
                    : distance < radius - 1 ? Material.GRASS_BLOCK : Material.CALCITE;
            return new CourseGrid.TerrainColumn(height, TileTypes.OUT_OF_BOUNDS, top, thickness, 0);
        }
    }

    ThemedCourse(CourseGenerationSettings settings, long seed, int count, CourseGrid.Layout layout) {
        this.settings = settings;
        this.seed = seed;
        var random = new Random(seed);
        phaseX = random.nextDouble() * 1024;
        phaseZ = random.nextDouble() * 1024;
        waterline = settings.terrain().altitude - (settings.terrain() == CourseTerrain.ISLAND ? 7 : settings.elevation() + 5);
        var rotation = new ArrayList<>(settings.archetypes());
        var generated = new ArrayList<Hole>(count);
        boolean standard = !layout.shortHoles();
        boolean featuredWoodland = settings.terrain() == CourseTerrain.WOODLAND && !layout.shortHoles();
        for (int index = 0; index < count; index++) {
            if (index % rotation.size() == 0) {
                Collections.shuffle(rotation, random);
                if (featuredWoodland) rotation.sort(Comparator.comparing(type -> WoodlandHole.forArchetype(type) == null));
                else if (standard)
                    rotation.sort(Comparator.comparing(type -> TerrainShowcase.forArchetype(settings.terrain(), type) == null));
            }
            HoleArchetype archetype = rotation.get(index % rotation.size());
            WoodlandHole woodlandHole = featuredWoodland ? WoodlandHole.forArchetype(archetype) : null;
            TerrainShowcase showcase = standard ? TerrainShowcase.forArchetype(settings.terrain(), archetype) : null;
            generated.add(plan(index, archetype, random, layout.shortHoles(), woodlandHole, showcase));
        }
        holes = List.copyOf(generated);
        var scenery = new ArrayList<List<TerrainLandmark>>(count);
        for (int index = 0; index < count; index++) scenery.add(planTerrainLandmarks(index));
        terrainLandmarks = List.copyOf(scenery);
    }

    private Hole plan(int index, HoleArchetype archetype, Random random, boolean shortLayout, WoodlandHole woodlandHole,
                      TerrainShowcase showcase) {
        int orientation = random.nextInt(8);
        int x0 = shortLayout ? random.nextInt(31, 40) : random.nextInt(13, 25);
        int length = shortLayout ? random.nextInt(archetype == HoleArchetype.ISLAND_GREEN ? 31 : 24, 46) : switch (archetype) {
            case TUNNEL, GAP_SHOT -> random.nextInt(34, 59);
            case AIR_SHOT, CLIFFSIDE, RISK_REWARD -> random.nextInt(62, 103 - x0);
            case ISLAND_GREEN -> random.nextInt(39, 93 - x0);
            default -> random.nextInt(39, 103 - x0);
        };
        int z0 = random.nextInt(41, 62), drift = shortLayout ? random.nextInt(-14, 15) : random.nextInt(-22, 23);
        int side = random.nextBoolean() ? 1 : -1;
        if (woodlandHole != null) {
            drift = woodlandHole == WoodlandHole.FERNBEND ? side * random.nextInt(24, 34) : 0;
            if (woodlandHole == WoodlandHole.FERNBEND) length = random.nextInt(61, 104 - x0);
        }
        boolean straightShowcase = showcase != null && showcase.straight();
        if (straightShowcase) drift = 0;
        GridPoint tee = orient(index, orientation, x0, z0);
        GridPoint basket = orient(index, orientation, x0 + length, z0 + drift);
        int bend = switch (archetype) {
            case DOGLEG -> random.nextInt(15, 24);
            case DOUBLE_FAIRWAY, RISK_REWARD -> random.nextInt(15, 20);
            case CLIFFSIDE -> random.nextInt(8, 15);
            default -> random.nextInt(0, 5);
        };
        if (shortLayout) bend = Math.min(13, bend);
        GridPoint near = orient(index, orientation, x0 + length * 2 / 5, z0 + drift * 2 / 5 + bend * side);
        GridPoint far = orient(index, orientation, x0 + length * 7 / 10, z0 + drift * 7 / 10 + bend * side);
        if (woodlandHole == WoodlandHole.FERNBEND) {
            near = orient(index, orientation, x0 + length * 3 / 5, z0);
            far = orient(index, orientation, x0 + length * 4 / 5, z0 + drift / 2);
        } else if (woodlandHole != null || straightShowcase) {
            near = orient(index, orientation, x0 + length * 2 / 5, z0);
            far = orient(index, orientation, x0 + length * 7 / 10, z0);
        }
        var route = List.of(tee, near, far, basket);
        List<GridPoint> alternative = switch (archetype) {
            case DOUBLE_FAIRWAY -> List.of(tee, orient(index, orientation, x0 + length / 2, z0 + drift / 2 - bend * side), basket);
            case RISK_REWARD -> List.of(tee, basket);
            default -> List.of();
        };
        double lane = settings.fairwayWidth() / 2.0 * switch (archetype) {
            case TUNNEL -> .69;
            case GAP_SHOT -> .8;
            default -> 1;
        };
        List<Landmark> landmarks = woodlandHole == null || settings.obstacleDensity() == 0 ? List.of()
                : landmarks(woodlandHole, index, orientation, x0, z0, length, side, lane, route,
                        new Random(seed ^ (index + 1L) * 341873128712L));
        var pools = new ArrayList<Pool>();
        double hazard = settings.hazardDensity();
        if (hazard > 0) {
            if (archetype == HoleArchetype.ISLAND_GREEN)
                pools.add(new Pool(basket, 9.5, 12.5 + hazard * 7, true));
            else if (archetype == HoleArchetype.AIR_SHOT || archetype == HoleArchetype.RISK_REWARD) {
                GridPoint carry = archetype == HoleArchetype.AIR_SHOT
                        ? interpolate(near, far, .5) : interpolate(tee, basket, .54);
                pools.add(new Pool(carry, 0, 3.2 + hazard * 6, true));
            }
            if (random.nextDouble() < hazard && !straightShowcase) {
                int pocketZ = woodlandHole == null ? z0 + drift / 2 - side * (17 + random.nextInt(6))
                        : Math.clamp(z0 - side * ((int)Math.ceil(lane) + 24), 17, CELL - 17);
                var pocket = orient(index, orientation, x0 + length / 2, pocketZ);
                double radius = 5 + hazard * 9;
                if (landmarks.stream().noneMatch(landmark -> squared(landmark.position(), pocket.x(), pocket.z())
                        < (radius + 5) * (radius + 5))) pools.add(new Pool(pocket, 0, radius, false));
            }
        }
        var guards = new ArrayList<GridPoint>();
        if (woodlandHole == null && !straightShowcase
                && (archetype == HoleArchetype.GAP_SHOT || archetype == HoleArchetype.GUARDIAN_GREEN)) {
            int at = archetype == HoleArchetype.GAP_SHOT ? length / 2 : length * 4 / 5;
            int offset = (int)Math.ceil(lane) + 7;
            for (int flank : new int[]{-1, 1})
                guards.add(orient(index, orientation, x0 + at, z0 + drift * at / length + flank * offset));
        }
        int elevation = settings.elevation();
        int base = settings.terrain().altitude;
        int teeY = base + (int)Math.round(noise(tee.x(), tee.z()) * elevation * .45);
        int difference = (int)Math.round((random.nextDouble() - .5) * elevation * 1.25);
        if (shortLayout) difference = Math.clamp(difference, -7, 7);
        else if (archetype == HoleArchetype.CLIFFSIDE && elevation > 0) difference += side * (elevation / 4);
        if (archetype == HoleArchetype.ELEVATED_BASKET) difference = Math.max(4, Math.abs(difference) / 2 + 4);
        int basketY = teeY + difference;
        if (settings.terrain() == CourseTerrain.ISLAND) {
            teeY = Math.max(waterline + 3, teeY);
            basketY = Math.max(waterline + 3, basketY);
        }
        double effort = pathLength(route) + Math.abs(difference);
        int par = shortLayout ? 1 : effort > 110 ? 5 : effort > 78 ? 4 : 3;
        CanyonArch arch = null;
        List<SkyIsland> islands = List.of();
        if (showcase != null && settings.obstacleDensity() > 0) {
            var scenery = new Random(seed ^ (index + 1L) * 132897987541L);
            if (showcase == TerrainShowcase.SANDSTONE_GATE) {
                var center = orient(index, orientation, x0 + length * scenery.nextInt(47, 62) / 100, z0);
                arch = new CanyonArch(center, tee.z() == basket.z(), (int)Math.ceil(lane) + scenery.nextInt(5, 8),
                        Math.max(teeY, basketY) + 5, scenery.nextInt(17, 21));
            } else if (settings.terrain() == CourseTerrain.SKY) {
                islands = skyIslands(index, orientation, x0, length, route, (teeY + basketY) / 2, scenery);
            }
        }
        return new Hole(archetype, route, alternative, List.copyOf(guards), List.copyOf(pools),
                teeY, basketY, lane, par, woodlandHole, landmarks, showcase, arch, islands);
    }

    private List<SkyIsland> skyIslands(int index, int orientation, int x0, int length, List<GridPoint> route,
                                       int height, Random random) {
        var result = new ArrayList<SkyIsland>();
        double mainlandReach = 18 + settings.fairwayWidth() * .42;
        for (int side : new int[]{-1, 1}) for (int offset : new int[]{25, 73}) {
            int x = x0 + length * offset / 100 + random.nextInt(-5, 6);
            int radius = random.nextInt(5, 10);
            int z = side < 0 ? random.nextInt(11, 18) : CELL - random.nextInt(11, 18);
            var center = orient(index, orientation, x, z);
            if (routeDistance(route, center.x(), center.z()) <= mainlandReach + radius + 3) continue;
            if (result.stream().anyMatch(other -> squared(other.center(), center.x(), center.z())
                    < Math.pow(other.radius() + radius + 4, 2))) continue;
            var elevated = new GridPoint(center.x(), height + random.nextInt(-13, 13), center.z());
            result.add(new SkyIsland(elevated, radius, random.nextInt(6, 15), random.nextInt(64)));
        }
        return List.copyOf(result);
    }

    private List<TerrainLandmark> planTerrainLandmarks(int index) {
        Hole hole = hole(index);
        if (hole.showcase() == null || settings.obstacleDensity() == 0) return List.of();
        var kind = TerrainLandmark.Kind.forShowcase(hole.showcase());
        if (kind == null) return List.of();
        var random = new Random(seed ^ (index + 1L) * 0x632be59bd9b4e019L);
        int preferredSegment = switch (kind) {
            case CAIRN, KEEP -> 2;
            case LIGHTHOUSE -> 0;
            default -> 1;
        };
        int flank = random.nextBoolean() ? 1 : -1;
        var landmarks = new ArrayList<TerrainLandmark>(2);
        tryLandmark(index, hole, kind, preferredSegment, flank, random, landmarks);
        if (landmarks.isEmpty() || kind == TerrainLandmark.Kind.GLACIER || kind == TerrainLandmark.Kind.BASALT
                || kind == TerrainLandmark.Kind.PALM)
            tryLandmark(index, hole, kind, preferredSegment, -flank, random, landmarks);
        return List.copyOf(landmarks);
    }

    private void tryLandmark(int index, Hole hole, TerrainLandmark.Kind kind, int preferredSegment, int flank,
                             Random random, List<TerrainLandmark> landmarks) {
        int startOffset = (int)Math.ceil(hole.width()) + kind.radius + 1;
        double preferredCenter = random.nextDouble(.36, .65);
        for (int attempt = 0; attempt < 3; attempt++) {
            int segment = (preferredSegment + attempt) % 3;
            GridPoint first = hole.route().get(segment), last = hole.route().get(segment + 1);
            int dx = last.x() - first.x(), dz = last.z() - first.z();
            double length = Math.hypot(dx, dz);
            for (double fraction : new double[]{preferredCenter, .25, .75}) {
                for (int offset = startOffset; offset <= startOffset + 12; offset += 2) {
                    int x = (int)Math.round(first.x() + dx * fraction - dz / length * flank * offset);
                    int z = (int)Math.round(first.z() + dz * fraction + dx / length * flank * offset);
                    if (!landmarkFootprint(index, hole, x, z, kind.radius)) continue;
                    int ground = sample(x, z, true).height();
                    int rotation = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? 0 : 2) : (dz >= 0 ? 1 : 3);
                    if (flank < 0) rotation = (rotation + 2) % 4;
                    landmarks.add(new TerrainLandmark(kind, new GridPoint(x, ground, z),
                            random.nextInt(kind.minHeight, kind.maxHeight), rotation));
                    return;
                }
            }
        }
    }

    private boolean landmarkFootprint(int index, Hole hole, int x, int z, int radius) {
        if (holeIndexAt(x - radius - 1, z - radius - 1) != index
                || holeIndexAt(x + radius + 1, z + radius + 1) != index) return false;
        if (hole.distance(x, z) < hole.width() + radius || hole.nearAnchor(x, z, 9 + radius)) return false;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            int checkX = x + dx, checkZ = z + dz;
            if (protectedLane(checkX, checkZ) || sample(checkX, checkZ, true).type().isHazard()) return false;
        }
        return true;
    }

    String showcaseHint(int index) {
        Hole hole = hole(index);
        if (hole.woodlandHole() != null) return hole.woodlandHole().hint(hole.route());
        if (hole.showcase() == null) return null;
        boolean featurePresent = switch (hole.showcase()) {
            case SANDSTONE_GATE -> hole.arch() != null;
            case CLOUD_CROSSING, DRIFTWOOD_GREEN, WRECK_LAGOON, LANTERN_CAY, EMBER_CROSSING ->
                    hole.pools().stream().anyMatch(Pool::crossesFairway);
            case GLACIER_PASS, BASALT_GATE -> terrainLandmarks.get(index).size() == 2;
            case ASHEN_KEEP -> !terrainLandmarks.get(index).isEmpty();
            default -> true;
        };
        return hole.showcase().hint(featurePresent);
    }

    ArchColumn archColumn(int x, int z, int ground) {
        Hole hole = at(x, z);
        if (hole == null || hole.arch() == null) return null;
        return hole.arch().column(x, z, ground);
    }

    GridPoint showcaseFocus(int index) {
        CanyonArch arch = hole(index).arch();
        if (arch != null) return new GridPoint(arch.center().x(), arch.spring() + arch.rise() / 2, arch.center().z());
        var marks = terrainLandmarks.get(index);
        if (marks.isEmpty()) return null;
        var mark = marks.getFirst();
        return new GridPoint(mark.base().x(), mark.base().y() + mark.height() * 2 / 3, mark.base().z());
    }

    TerrainLandmark terrainLandmarkAt(int x, int z, int padding) {
        int index = holeIndexAt(x, z);
        if (index < 0) return null;
        for (var mark : terrainLandmarks.get(index)) if (mark.covers(x, z, padding)) return mark;
        return null;
    }

    private static List<Landmark> landmarks(WoodlandHole hole, int index, int orientation, int x, int z, int length,
                                            int turn, double laneWidth, List<GridPoint> route, Random random) {
        int setback = (int)Math.ceil(laneWidth) + Landmark.RADIUS + 1;
        var result = new ArrayList<Landmark>();
        switch (hole) {
            case CATHEDRAL_RUN -> {
                for (int flank : new int[]{-1, 1}) for (int at : new int[]{3, 7}) {
                    int distance = Math.clamp(length * at / 10 + random.nextInt(-2, 3), 15, length - 15);
                    var point = orient(index, orientation, x + distance,
                            z + flank * (setback + random.nextInt(2)));
                    result.add(new Landmark(LandmarkType.OLD_GROWTH_PINE, point, random.nextInt(9, 13)));
                }
            }
            case FERNBEND -> {
                int bend = x + length * 3 / 5 - 3;
                for (int offset = setback; offset <= setback + 14; offset++) {
                    if (z + turn * offset < 6 || z + turn * offset >= CELL - 6) break;
                    var point = orient(index, orientation, bend, z + turn * offset);
                    if (routeDistance(route, point.x(), point.z()) < laneWidth + 5) continue;
                    result.add(new Landmark(LandmarkType.MOSSY_BOULDER, point, random.nextInt(4, 7)));
                    break;
                }
            }
            case MOSSGATE -> {
                int crossing = x + length * random.nextInt(47, 60) / 100;
                for (int flank : new int[]{-1, 1}) {
                    var point = orient(index, orientation, crossing, z + flank * setback);
                    result.add(new Landmark(LandmarkType.RUINED_GATEPOST, point, random.nextInt(4, 7)));
                }
            }
        }
        return List.copyOf(result);
    }

    boolean nearLandmark(int x, int z, int clearance) {
        Hole hole = at(x, z);
        if (hole == null) return false;
        int reach = Landmark.RADIUS + clearance;
        return terrainLandmarkAt(x, z, clearance) != null
                || hole.landmarks().stream().anyMatch(landmark -> Math.abs(x - landmark.position().x()) <= reach
                && Math.abs(z - landmark.position().z()) <= reach);
    }

    private static GridPoint orient(int index, int orientation, int x, int z) {
        if (orientation >= 4) x = CELL - x;
        int column = MARGIN + index % COLUMNS * CELL, row = MARGIN + index / COLUMNS * CELL;
        return switch (orientation % 4) {
            case 1 -> new GridPoint(column + CELL - z, 0, row + x);
            case 2 -> new GridPoint(column + CELL - x, 0, row + CELL - z);
            case 3 -> new GridPoint(column + z, 0, row + CELL - x);
            default -> new GridPoint(column + x, 0, row + z);
        };
    }

    Hole hole(int index) { return holes.get(index); }
    CourseGenerationSettings settings() { return settings; }

    private int holeIndexAt(int x, int z) {
        int column = Math.floorDiv(x - MARGIN, CELL), row = Math.floorDiv(z - MARGIN, CELL);
        if (column < 0 || column >= COLUMNS || row < 0 || row >= (holes.size() + 2) / 3) return -1;
        int index = row * COLUMNS + column;
        return index < holes.size() ? index : -1;
    }

    private Hole at(int x, int z) {
        int index = holeIndexAt(x, z);
        return index < 0 ? null : holes.get(index);
    }

    double routeDistance(double x, double z) {
        Hole hole = at((int)Math.floor(x), (int)Math.floor(z));
        return hole == null ? Double.POSITIVE_INFINITY : hole.distance(x, z);
    }

    boolean protectedLane(int x, int z) {
        Hole hole = at(x, z);
        return hole != null && (hole.nearAnchor(x, z, 9) || hole.distance(x, z) < hole.width());
    }

    CourseGrid.TerrainColumn sample(int x, int z, boolean inBounds) {
        var terrain = settings.terrain();
        Hole hole = at(x, z);
        double route = hole == null ? Double.POSITIVE_INFINITY : hole.distance(x, z);
        double local = noise(x, z);
        boolean nearAnchor = hole != null && hole.nearAnchor(x, z, 9);
        double mass = switch (terrain) {
            case SKY -> 13 + settings.fairwayWidth() * .42 + local * 5;
            case ISLAND -> 20 + settings.fairwayWidth() * .50 + local * 8;
            default -> Double.POSITIVE_INFINITY;
        };
        boolean exposed = !nearAnchor && route > mass;
        boolean fairway = nearAnchor || hole != null && route < hole.width();
        Pool pool = hole == null || nearAnchor ? null : poolAt(hole, x, z, fairway);
        if (terrain == CourseTerrain.SKY && (exposed || pool != null || !inBounds)) {
            // Satellites remain out of bounds so a ricochet or a landing cannot grant a lie on them.
            if (exposed && inBounds && hole != null) for (SkyIsland island : hole.islands()) {
                var column = island.column(x, z);
                if (column != null) return column;
            }
            return new CourseGrid.TerrainColumn(65, TileTypes.OUT_OF_BOUNDS, Material.AIR, 0, 0);
        }
        if (terrain == CourseTerrain.ISLAND && (exposed || !inBounds) || pool != null) {
            Material liquid = terrain == CourseTerrain.NETHER ? Material.LAVA : Material.WATER;
            int y = terrain == CourseTerrain.ISLAND || pool == null ? waterline : poolLevel(hole, pool);
            TileTypes type = !inBounds ? TileTypes.OUT_OF_BOUNDS : liquid == Material.LAVA ? TileTypes.LAVA : TileTypes.WATER;
            return new CourseGrid.TerrainColumn(y, type, liquid, terrain == CourseTerrain.ISLAND ? 14 : 12, 5);
        }

        int y = surfaceHeight(hole, x, z, route, local);
        int depth = terrain == CourseTerrain.SKY
                ? Math.clamp((int)Math.round((mass - route) * .77), 4, 20)
                : terrain == CourseTerrain.MOUNTAIN ? 32 : 16;
        boolean pin = hole != null && squared(hole.basket(), x, z) < 24;
        TileTypes type = !inBounds ? TileTypes.OUT_OF_BOUNDS : pin ? TileTypes.PIN
                : fairway ? TileTypes.FAIRWAY : TileTypes.HEAVY_ROUGH;
        Material top = pin ? terrain.green : top(terrain, y, x, z, local, fairway, mass - route);
        return new CourseGrid.TerrainColumn(y, type, top, depth, 0);
    }

    private int poolLevel(Hole hole, Pool pool) {
        GridPoint center = pool.center();
        return surfaceHeight(hole, center.x(), center.z(), hole.distance(center.x(), center.z()),
                noise(center.x(), center.z())) - 5;
    }

    private Pool poolAt(Hole hole, int x, int z, boolean fairway) {
        for (Pool pool : hole.pools()) {
            if (fairway && !pool.crossesFairway()) continue;
            double distance = squared(pool.center(), x, z);
            if (distance >= pool.innerRadius() * pool.innerRadius() && distance < pool.radius() * pool.radius())
                return pool;
        }
        return null;
    }

    private int surfaceHeight(Hole hole, int x, int z, double route, double local) {
        var terrain = settings.terrain();
        double rough = terrain.altitude + local * settings.elevation();
        if (hole == null) return (int)Math.round(rough);
        double tee = squared(hole.tee(), x, z), pin = squared(hole.basket(), x, z);
        if (tee <= 49) return hole.teeY();
        if (pin <= 49) return hole.basketY();
        double factor = Math.clamp((route - hole.width() * .65) / 15, 0, 1);
        double headingX = hole.basket().x() - hole.tee().x(), headingZ = hole.basket().z() - hole.tee().z();
        double projection = ((x - hole.tee().x()) * headingX + (z - hole.tee().z()) * headingZ)
                / (headingX * headingX + headingZ * headingZ);
        double t = Math.clamp((projection - .12) / .76, 0, 1);
        double height = hole.teeY() + (hole.basketY() - hole.teeY()) * t;
        height += factor * (rough - height);
        if (hole.archetype() == HoleArchetype.CLIFFSIDE && route > hole.width()
                && (x - hole.tee().x()) * headingZ - (z - hole.tee().z()) * headingX < 0)
            height -= Math.min(1, (route - hole.width()) / 11) * (settings.elevation() * .50);
        if (hole.showcase() == TerrainShowcase.PAINTED_RIM || hole.showcase() == TerrainShowcase.SPLIT_MESA) {
            double rim = Math.clamp((route - hole.width() - 2 + local * 2) / 15, 0, 1);
            height += Math.floor(rim * 5) * settings.elevation() * .26;
        }
        if (terrain == CourseTerrain.ISLAND) height = Math.max(waterline + 1, height);
        if (terrain == CourseTerrain.SKY && !hole.nearAnchor(x, z, 9) && route > 15) height -= Math.min(6, (route - 15) * .5);
        return (int)Math.round(height);
    }

    private static Material top(CourseTerrain terrain, int y, int x, int z, double local, boolean fairway, double shore) {
        return switch (terrain) {
            case MOUNTAIN -> fairway ? Material.GRASS_BLOCK : y > terrain.altitude + 8 ? Material.SNOW_BLOCK
                    : local > -.15 ? Material.STONE : Material.COARSE_DIRT;
            case ISLAND -> shore < 5 || y < terrain.altitude - 3 ? Material.SAND
                    : !fairway && local > .24 ? Material.PODZOL : terrain.surface;
            case DESERT -> fairway && Math.sin((x - z) / 8.0) > .4 ? Material.SMOOTH_SANDSTONE
                    : local > .23 ? terrain.accent : terrain.surface;
            default -> !fairway && local > .16 ? terrain.accent : terrain.surface;
        };
    }

    private double noise(int x, int z) {
        double a = x + phaseX, b = z + phaseZ;
        return Math.sin(a / 30.0) * .41 + Math.cos(b / 37.0) * .36 + Math.sin((a + b) / 22.0) * .23;
    }

    int difficulty() {
        double mean = holes.stream().mapToDouble(hole -> 8 + pathLength(hole.route()) * .28
                + Math.abs(hole.teeY() - hole.basketY()) * 1.05 + Math.max(0, 12 - hole.width()) * 2.3
                + settings.obstacleDensity() * 21 + hole.pools().size() * 5
                - (hole.alternative().isEmpty() ? 0 : 4)
                + (settings.terrain() == CourseTerrain.SKY || settings.terrain() == CourseTerrain.ISLAND ? 8 : 0)).average().orElse(0);
        return Math.clamp((int)Math.round(mean), 1, 100);
    }

    private static GridPoint interpolate(GridPoint a, GridPoint b, double t) {
        return new GridPoint((int)Math.round(a.x() + (b.x() - a.x()) * t), 0,
                (int)Math.round(a.z() + (b.z() - a.z()) * t));
    }

    private static double squared(GridPoint point, double x, double z) {
        double dx = x - point.x(), dz = z - point.z();
        return dx * dx + dz * dz;
    }

    private static double routeDistance(List<GridPoint> points, double x, double z) {
        double closest = Double.POSITIVE_INFINITY;
        for (int i = 1; i < points.size(); i++) {
            GridPoint a = points.get(i - 1), b = points.get(i);
            closest = Math.min(closest, CourseGrid.segmentDistance(x, z, a.x(), a.z(), b.x(), b.z()));
        }
        return closest;
    }

    private static double pathLength(List<GridPoint> points) {
        double length = 0;
        for (int i = 1; i < points.size(); i++)
            length += Math.sqrt(squared(points.get(i - 1), points.get(i).x(), points.get(i).z()));
        return length;
    }
}
