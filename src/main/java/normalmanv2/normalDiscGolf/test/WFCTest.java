package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.CourseDifficulty;
import normalmanv2.normalDiscGolf.impl.course.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.Tile;
import normalmanv2.normalDiscGolf.impl.course.TileTypes;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class WFCTest implements CommandExecutor {
    private final NormalDiscGolf plugin;

    public WFCTest(NormalDiscGolf plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {

        if (args.length != 2) {
            if (commandSender instanceof Player player) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4Please enter a world name followed by a difficulty ( \"easy\" | \"medium\" | \"hard\" )! "));
            }
            return false;
        }

        String worldName = args[0];
        String difficulty = args[1];
        CourseDifficulty courseDifficulty;

        if (!(difficulty.equalsIgnoreCase("easy") || difficulty.equalsIgnoreCase("medium") || difficulty.equalsIgnoreCase("hard"))) return false;

        if (difficulty.equalsIgnoreCase("easy")) {
            courseDifficulty = CourseDifficulty.EASY;
        } else if (difficulty.equalsIgnoreCase("medium")) {
            courseDifficulty = CourseDifficulty.MEDIUM;
        } else {
            courseDifficulty = CourseDifficulty.HARD;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.createWorld(new EmptyWorldCreator(worldName));
        }

        World finalWorld = world;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<TileTypes> tileTypes = Arrays.asList(TileTypes.FAIRWAY, TileTypes.OBSTACLE, TileTypes.WATER, TileTypes.OUT_OF_BOUNDS);
            CourseGrid courseGrid = new CourseGrid(8, 8, tileTypes, finalWorld);
            Location startingLocation = new Location(finalWorld, 0, 64, 0);
            CourseImpl courseImpl = new CourseImpl(courseDifficulty, "Test_Course", 18, startingLocation, courseGrid);
            courseImpl.generateCourseGrid(NDGManager.getInstance().getObstacleRegistry());

            for (int x = 0; x < courseGrid.getWidth(); x++) {
                for (int z = 0; z < courseGrid.getDepth(); z++) {
                    Tile tile = courseGrid.getTile(x, z);
                    placeTileInWorld(finalWorld, tile.getLocation(), tile.getCollapsedState());
                }
            }
            Player player = (Player) commandSender;
            player.teleport(startingLocation);
            courseGrid.printCourse();
        }, 60);

        return true;
    }

    private void placeTileInWorld(World world, Location location, TileTypes type) {
        int startX = location.getBlockX();
        int startZ = location.getBlockZ();
        int y = location.getBlockY();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Location blockLocation = new Location(world, startX + x, y, startZ + z);
                switch (type) {
                    case FAIRWAY ->
                        world.getBlockAt(blockLocation).setType(Material.GRASS_BLOCK);

                    case OBSTACLE ->
                        world.getBlockAt(blockLocation).setType(Material.STONE);

                    case WATER ->
                        world.getBlockAt(blockLocation).setType(Material.WATER);

                    case TEE ->
                        world.getBlockAt(blockLocation).setType(Material.GREEN_STAINED_GLASS);

                    case PIN ->
                        world.getBlockAt(blockLocation).setType(Material.PINK_STAINED_GLASS);

                    case OUT_OF_BOUNDS ->
                        world.getBlockAt(blockLocation).setType(Material.BLACK_STAINED_GLASS);

                }
            }
        }
    }
}
