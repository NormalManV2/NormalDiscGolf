package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.CourseDifficulty;
import normalmanv2.normalDiscGolf.impl.course.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.Tile;
import normalmanv2.normalDiscGolf.impl.course.TileTypes;
import org.bukkit.Bukkit;
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
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {

        World world = Bukkit.getWorld("Test_Course");
        if (world == null) {
            world = Bukkit.createWorld(new EmptyWorldCreator("Test_Course"));
        }

        World finalWorld = world;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<TileTypes> tileTypes = Arrays.asList(TileTypes.FAIRWAY, TileTypes.OBSTACLE, TileTypes.WATER);
            CourseGrid courseGrid = new CourseGrid(20, 20, tileTypes);
            Location startingLocation = new Location(finalWorld, 0, 64, 0);
            CourseImpl courseImpl = new CourseImpl(CourseDifficulty.EASY, "Test_Course", 18, startingLocation, courseGrid);
            courseImpl.generateCourseGrid();

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

                }
            }
        }
    }
}
