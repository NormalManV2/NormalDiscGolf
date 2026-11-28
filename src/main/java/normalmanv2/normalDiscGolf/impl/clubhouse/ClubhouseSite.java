package normalmanv2.normalDiscGolf.impl.clubhouse;

import normalmanv2.normalDiscGolf.impl.util.BukkitCompatibility;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.util.BoundingBox;

record ClubhouseSite(World world, int x, int y, int z) {
    private static final int HALF_WIDTH = 12;
    private static final int LENGTH = 24;
    private static final int BASKET_Z = 20;
    private static final int BOARD_X = 10;
    private static final int BOARD_Z = 4;
    private static final int MAX_ENTRANCE_STEPS = 12;
    private static final int[] DISTANCES = {4, 8, 12};

    static ClubhouseSite find(World world) {
        var spawn = world.getSpawnLocation();
        ClubhouseSite fallback = null;
        for (int[] offset : new int[][]{{0, 18}, {30, -5}, {-30, -5}, {0, -42}}) {
            int x = spawn.getBlockX() + offset[0], z = spawn.getBlockZ() + offset[1];
            if (!world.getWorldBorder().isInside(new Location(world, x - HALF_WIDTH, spawn.getY(), z))
                    || !world.getWorldBorder().isInside(new Location(world, x + HALF_WIDTH, spawn.getY(), z + LENGTH))) continue;
            int y = world.getMinHeight();
            for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) for (int dz = 0; dz <= LENGTH; dz++)
                y = Math.max(y, world.getHighestBlockYAt(x + dx, z + dz, HeightMap.WORLD_SURFACE) + 1);
            if (y + 7 >= world.getMaxHeight()) continue;
            var candidate = new ClubhouseSite(world, x, y, z);
            if (candidate.entranceDepth() > 0) return candidate;
            if (fallback == null) fallback = candidate;
        }
        return fallback;
    }

    void build() {
        int entranceDepth = entranceDepth();
        for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) for (int dz = 0; dz <= LENGTH; dz++) {
            boolean edge = Math.abs(dx) == HALF_WIDTH || dz == 0 || dz == LENGTH;
            Material floor = edge ? Material.MOSSY_STONE_BRICKS : dz < 7 ? Material.SPRUCE_PLANKS
                    : Math.abs(dx) < 7 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK;
            place(dx, 0, dz, floor);
            if (edge && !(entranceDepth > 0 && dz == 0 && Math.abs(dx) < 2))
                place(dx, 1, dz, Material.SPRUCE_FENCE);
        }
        for (int side : new int[]{-HALF_WIDTH, HALF_WIDTH}) for (int dz : new int[]{0, 12, LENGTH}) {
            for (int dy = -1; dy >= -12 && y + dy >= world.getMinHeight() && block(side, dy, dz).isEmpty(); dy--)
                place(side, dy, dz, Material.SPRUCE_LOG);
        }
        buildEntrance(entranceDepth);

        counter(-8, Material.DARK_OAK_PLANKS, "§6Pro Shop", "Rotating stock", "Right-click");
        counter(8, Material.OAK_PLANKS, "§bDaily Course", "Leaderboard", "Right-click");
        sign(BOARD_X, 1, BOARD_Z, "§bDaily Top 3", "", "", "");
        place(0, 1, 5, Material.STRIPPED_SPRUCE_WOOD);
        sign(0, 2, 5, "§aPutting Green", "Try any disc", "Right-click", "");

        for (int distance : DISTANCES) {
            int dz = BASKET_Z - distance;
            for (int dx = -1; dx <= 1; dx++) set(dx, 0, dz, Material.SMOOTH_STONE);
            sign(2, 1, dz, "§a" + distance + " blocks", "Right-click to", "aim a practice", "throw");
        }

        set(0, 0, BASKET_Z, Material.GREEN_TERRACOTTA);
        place(0, 1, BASKET_Z, Material.IRON_BARS);
        place(0, 2, BASKET_Z, Material.CHAIN);
        place(0, 3, BASKET_Z, Material.YELLOW_CONCRETE);
        for (int[] arm : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            place(arm[0], 1, BASKET_Z + arm[1], Material.IRON_TRAPDOOR);
            place(arm[0], 3, BASKET_Z + arm[1], Material.YELLOW_STAINED_GLASS);
        }
        for (int side : new int[]{-1, 1}) for (int dz : new int[]{9, 15, 22}) {
            place(side * 9, 1, dz, Material.MOSSY_COBBLESTONE_WALL);
            place(side * 9, 2, dz, Material.LANTERN);
            place(side * 10, 1, dz, Material.AZALEA);
            place(side * 10, 1, dz - 1, Material.FERN);
        }
    }

    private int entranceDepth() {
        for (int step = 1; step <= MAX_ENTRANCE_STEPS; step++) {
            if (y - step < world.getMinHeight()
                    || !world.getWorldBorder().isInside(new Location(world, x + .5, y - step, z - step + .5))) break;
            Block tread = block(0, -step, -step);
            if (!tread.getRelative(BlockFace.UP).isEmpty() || !tread.getRelative(0, 2, 0).isEmpty()) break;
            if (tread.getType().isSolid()) return step;
            if (!tread.isEmpty()) break;
        }
        return 0;
    }

    private void buildEntrance(int depth) {
        var stairs = (Stairs) Material.SPRUCE_STAIRS.createBlockData();
        stairs.setFacing(BlockFace.SOUTH);
        for (int step = depth - 1; step >= 1; step--) {
            for (int dx = -1; dx <= 1; dx++) {
                Block tread = block(dx, -step, -step);
                if (tread.isEmpty() && tread.getRelative(BlockFace.UP).isEmpty()
                        && tread.getRelative(0, 2, 0).isEmpty()) tread.setBlockData(stairs, false);
            }
        }
        for (int side : new int[]{-2, 2}) {
            place(side, 2, 0, Material.SPRUCE_FENCE);
            place(side, 3, 0, Material.LANTERN);
        }
    }

    private void counter(int center, Material timber, String title, String line, String action) {
        for (int dx = -2; dx <= 2; dx++) place(center + dx, 1, 3, timber);
        for (int side : new int[]{-2, 2}) for (int dy = 2; dy <= 4; dy++)
            place(center + side, dy, 4, Material.STRIPPED_SPRUCE_LOG);
        for (int dx = -3; dx <= 3; dx++) for (int dz = 2; dz <= 6; dz++)
            place(center + dx, dz == 2 || dz == 6 ? 4 : 5, dz, Material.SPRUCE_SLAB);
        place(center, 1, 4, Material.BARREL);
        sign(center, 2, 3, title, line, action, "");
        place(center - 1, 2, 3, Material.LANTERN);
    }

    private void sign(int dx, int dy, int dz, String... lines) {
        Block block = block(dx, dy, dz);
        place(dx, dy, dz, Material.SPRUCE_SIGN);
        if (!(block.getState() instanceof Sign sign)) return;
        var direction = (Rotatable) block.getBlockData();
        direction.setRotation(BlockFace.NORTH);
        block.setBlockData(direction, false);
        updateSign(dx, dy, dz, lines);
    }

    private void updateSign(int dx, int dy, int dz, String... lines) {
        if (!(block(dx, dy, dz).getState() instanceof Sign sign)) return;
        boolean changed = false;
        for (int i = 0; i < lines.length; i++) if (!lines[i].equals(sign.getLine(i))) {
            sign.setLine(i, lines[i]);
            changed = true;
        }
        if (changed || BukkitCompatibility.needsWax(sign)) {
            BukkitCompatibility.setWaxed(sign, true);
            sign.update(false, false);
        }
    }

    int boardChunkX() { return (x + BOARD_X) >> 4; }
    int boardChunkZ() { return (z + BOARD_Z) >> 4; }
    void updateBoard(String[] lines) { updateSign(BOARD_X, 1, BOARD_Z, lines); }

    private void place(int dx, int dy, int dz, Material material) {
        Block block = block(dx, dy, dz);
        if (block.isEmpty()) block.setType(material, false);
    }

    private void set(int dx, int dy, int dz, Material material) {
        Block block = block(dx, dy, dz);
        if (block.getType() == Material.MOSS_BLOCK) block.setType(material, false);
    }

    private Block block(int dx, int dy, int dz) { return world.getBlockAt(x + dx, y + dy, z + dz); }

    Location entrance() { return new Location(world, x + .5, y + 1, z + 1.5, 0, 0); }
    Location tee(int distance) { return new Location(world, x + .5, y + 1, z + BASKET_Z - distance + .5, 0, 0); }
    Location basket() { return new Location(world, x + .5, y + 1.9, z + BASKET_Z + .5); }
    BoundingBox flightBounds() { return new BoundingBox(x - 11, y, z + 7, x + 12, y + 10, z + LENGTH); }

    boolean owns(Block block) {
        return block.getWorld().equals(world) && Math.abs(block.getX() - x) <= HALF_WIDTH
                && block.getY() >= y && block.getY() <= y + 5 && block.getZ() >= z && block.getZ() <= z + LENGTH;
    }

    int station(Block block) {
        if (!owns(block) || block.getType() != Material.SPRUCE_SIGN) return 0;
        int dx = block.getX() - x, dy = block.getY() - y, dz = block.getZ() - z;
        if (dx == -8 && dy == 2 && dz == 3) return -1;
        if (dx == 8 && dy == 2 && dz == 3 || dx == BOARD_X && dy == 1 && dz == BOARD_Z) return -2;
        if (dx == 0 && dy == 2 && dz == 5) return -3;
        if (dx == 2 && dy == 1) for (int distance : DISTANCES) if (dz == BASKET_Z - distance) return distance;
        return 0;
    }
}
