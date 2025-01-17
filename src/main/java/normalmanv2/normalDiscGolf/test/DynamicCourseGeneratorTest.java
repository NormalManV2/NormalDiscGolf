package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.common.command.AbstractCommand;
import normalmanv2.normalDiscGolf.impl.course.test.DynamicCourseGenerator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Logger;

public class DynamicCourseGeneratorTest extends AbstractCommand {

    private final Logger logger;

    public DynamicCourseGeneratorTest(Logger logger) {
        super("dynamictest", new String[]{"dt", "ndgdt", "dtest"}, "Usage: [ dynamictest | dt | ndgdt | dtest ]", "ndg.commands.admin.test");
        this.logger = logger;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {

        if (sender instanceof Player player) player.sendMessage("Command Was Heard");

        System.out.println("Course Generation Initiated!");
        DynamicCourseGenerator generator = new DynamicCourseGenerator(18);
        generator.generateCourse();
        generator.printGrid(this.logger);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return null;
    }
}
