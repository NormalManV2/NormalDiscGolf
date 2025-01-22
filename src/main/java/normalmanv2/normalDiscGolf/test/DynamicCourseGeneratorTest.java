package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import normalmanv2.normalDiscGolf.common.command.AbstractCommand;
import normalmanv2.normalDiscGolf.impl.course.test.DynamicCourseGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Logger;

public class DynamicCourseGeneratorTest extends AbstractCommand {

    private final NormalDiscGolf plugin;

    public DynamicCourseGeneratorTest(NormalDiscGolf plugin) {
        super("dynamictest", new String[]{"dt", "ndgdt", "dtest"}, "Usage: [ dynamictest | dt | ndgdt | dtest ]", "ndg.commands.admin.test");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players may execute this command!");
            return;
        }

        World world = Bukkit.createWorld(new EmptyWorldCreator("Test_Course"));

        Bukkit.getScheduler().runTaskLater(plugin , () ->{
            Location spawnLoc = new Location(world, 0, 64, 0);
            if (world == null) throw new RuntimeException("World is null when trying to generate course!");
            world.setSpawnLocation(spawnLoc);

            System.out.println("Course Generation Initiated!");
            DynamicCourseGenerator generator = new DynamicCourseGenerator(18, world);
            generator.generateCourse();
            generator.printGrid();
            generator.createTileMesh(world);

            player.teleport(world.getSpawnLocation());
        }, 20);


    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return null;
    }
}
