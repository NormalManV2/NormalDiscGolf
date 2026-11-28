package normalmanv2.normalDiscGolf.impl.course.grid;

import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid.GridPoint;
import org.bukkit.Material;

record TerrainLandmark(Kind kind, GridPoint base, int height, int rotation) {
    enum Kind {
        GLACIER(4, 10, 15), CRAG(5, 14, 19), CAIRN(3, 7, 10),
        WRECK(5, 7, 10), LIGHTHOUSE(4, 10, 14), PALM(6, 7, 10),
        BASALT(3, 11, 16), VENT(4, 9, 14), KEEP(4, 8, 12);

        final int radius;
        final int minHeight;
        final int maxHeight;

        Kind(int radius, int minHeight, int maxHeight) {
            this.radius = radius;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
        }

        static Kind forShowcase(TerrainShowcase showcase) {
            return switch (showcase) {
                case GLACIER_PASS -> GLACIER;
                case EAGLE_RIDGE -> CRAG;
                case SUMMIT_STEPS -> CAIRN;
                case WRECK_LAGOON -> WRECK;
                case LANTERN_CAY -> LIGHTHOUSE;
                case PALM_COVE -> PALM;
                case BASALT_GATE -> BASALT;
                case EMBER_CROSSING -> VENT;
                case ASHEN_KEEP -> KEEP;
                default -> null;
            };
        }
    }

    boolean covers(int x, int z, int padding) {
        return Math.abs(x - base.x()) <= kind.radius + padding && Math.abs(z - base.z()) <= kind.radius + padding;
    }

    Material materialAt(int x, int y, int z) {
        int dx = x - base.x(), dz = z - base.z();
        int u = switch (rotation) { case 1 -> dz; case 2 -> -dx; case 3 -> -dz; default -> dx; };
        int v = switch (rotation) { case 1 -> -dx; case 2 -> -dz; case 3 -> dx; default -> dz; };
        int level = Math.max(1, y - base.y());
        if (level > height) return null;
        return switch (kind) {
            case GLACIER, CRAG, BASALT, VENT -> rock(u, level, v);
            case CAIRN -> cairn(u, level, v);
            case WRECK -> wreck(u, level, v);
            case LIGHTHOUSE -> lighthouse(u, level, v);
            case PALM -> palm(u, level, v);
            case KEEP -> keep(u, level, v);
        };
    }

    private Material rock(int x, int y, int z) {
        int edge = Math.floorMod(x * 3 + z * 5 + height, 4);
        double distance = Math.hypot(x, z * 1.24);
        double shoulder = kind == Kind.BASALT ? .76 : .47;
        int top = height - (int)Math.round(distance * height * shoulder / kind.radius) - (distance == 0 ? 0 : edge);
        if (distance > kind.radius || y > top) return null;
        return switch (kind) {
            case GLACIER -> y == top && edge == 0 ? Material.SNOW_BLOCK
                    : y > top - 4 || edge == 1 ? Material.BLUE_ICE : Material.PACKED_ICE;
            case CRAG -> y >= top - 1 ? Material.SNOW_BLOCK : edge == 0 ? Material.CALCITE
                    : edge == 1 ? Material.DIORITE : Material.STONE;
            case BASALT -> edge == 0 && y < top - 2 ? Material.BLACKSTONE : Material.POLISHED_BASALT;
            case VENT -> y >= top - 2 ? Material.MAGMA_BLOCK
                    : edge == 1 && y % 4 == 0 ? Material.SHROOMLIGHT : Material.BLACKSTONE;
            default -> throw new IllegalStateException("Expected a rock landmark");
        };
    }

    private Material cairn(int x, int y, int z) {
        int edge = Math.max(Math.abs(x), Math.abs(z));
        if (y == height) return edge == 0 ? Material.LANTERN : null;
        int tier = y <= 2 ? 3 : y <= height / 2 ? 2 : 1;
        if (edge > tier) return null;
        return y == 1 || y == height - 1 ? Material.POLISHED_ANDESITE
                : y % 2 == 0 ? Material.COBBLESTONE : Material.MOSSY_STONE_BRICKS;
    }

    private Material wreck(int x, int y, int z) {
        if (y == 1 && Math.abs(x) <= 5 && Math.abs(z) <= (Math.abs(x) > 3 ? 1 : 2))
            return Material.DARK_OAK_PLANKS;
        if (y <= 3 && Math.abs(x) <= 4 && Math.abs(z) == 2 && Math.floorMod(x + y, 3) != 0)
            return Material.DARK_OAK_WOOD;
        if (x == 0 && z == 0) return Material.STRIPPED_DARK_OAK_LOG;
        return z == 0 && x > 0 && x <= 3 && y >= height - 3 && y <= height - (x % 3)
                ? Material.LIGHT_GRAY_WOOL : null;
    }

    private Material lighthouse(int x, int y, int z) {
        int edge = Math.abs(x) + Math.abs(z);
        if (y == height) return edge <= 2 ? Material.WAXED_OXIDIZED_COPPER : null;
        if (y >= height - 2) return edge == 0 ? Material.SEA_LANTERN
                : edge == 1 ? Material.CYAN_STAINED_GLASS : null;
        if (edge > (y <= 2 ? 4 : 2)) return null;
        return y % 4 == 0 ? Material.CUT_RED_SANDSTONE : Material.SMOOTH_SANDSTONE;
    }

    private Material palm(int x, int y, int z) {
        int lean = y < height / 2 ? 0 : y < height - 2 ? 1 : 2;
        if (z == 0 && (x == lean || x == lean - 1 && (y == height / 2 || y == height - 2)))
            return Material.STRIPPED_JUNGLE_LOG;
        int dx = Math.abs(x - 2), dz = Math.abs(z);
        return y >= height - 2 && dx + dz <= (y == height - 2 ? 4 : 3)
                && (dx <= 1 || dz <= 1) ? Material.JUNGLE_LEAVES : null;
    }

    private Material keep(int x, int y, int z) {
        int edge = Math.max(Math.abs(x), Math.abs(z));
        if (edge > 3) return null;
        if (y <= 2) return Material.CHISELED_POLISHED_BLACKSTONE;
        if (x == 0 && z == 0) return y <= height - 2 ? Material.SHROOMLIGHT : null;
        if (z <= -2 && Math.abs(x) <= 1 && y <= 5) return null;
        if (edge < 2 || y > height - Math.floorMod(x * 3 + z, 4)) return null;
        return Math.abs(x) == 3 && Math.abs(z) == 3 ? Material.POLISHED_BLACKSTONE
                : Material.CRACKED_POLISHED_BLACKSTONE_BRICKS;
    }
}
