package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.course.test.CourseTest;
import normalmanv2.normalDiscGolf.impl.course.test.DiscGolfChunkGenerator;
import normalmanv2.normalDiscGolf.impl.course.test.DynamicCourseGenerator;
import normalmanv2.normalDiscGolf.impl.course.test.Terrain;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WFCTest implements CommandExecutor {
    private final NormalDiscGolfPlugin plugin;

    public WFCTest(NormalDiscGolfPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
        World mainWorld = Bukkit.getWorld("world");
        DynamicCourseGenerator courseGenerator = new DynamicCourseGenerator(18, mainWorld);
        CourseTest course = courseGenerator.generateCourse();
        Terrain[][] terrainGrid = course.getTerrainGrid();
        Location courseOrigin = courseGenerator.getSuperPosition();
        int tileSize = 8;

        // Create a new world for the disc golf course.
        WorldCreator worldCreator = new WorldCreator("discGolfWorld");
        worldCreator.generator(new DiscGolfChunkGenerator(terrainGrid, courseOrigin, tileSize));
        World discGolfWorld = worldCreator.createWorld();
        Player player = (Player) commandSender;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            discGolfWorld.setSpawnLocation(courseOrigin);
            player.teleport(courseOrigin);
        }, 20);
    return true;
    }
}
