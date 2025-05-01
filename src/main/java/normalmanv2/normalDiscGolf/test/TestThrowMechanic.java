package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.common.command.AbstractCommand;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.impl.course.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.disc.Driver;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.team.TeamImpl;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TestThrowMechanic extends AbstractCommand {

    private final NormalDiscGolfPlugin plugin;

    public TestThrowMechanic(NormalDiscGolfPlugin plugin) {
        super("testThrow", new String[]{"", "", ""}, "test throw", "ndg.mechanic.use");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {

        Player player = (Player) sender;

        if (player == null) return;

        //testWorld.setSpawnLocation(new Location(testWorld, 0, 0, 0));
        CourseGrid grid = new CourseGrid(16, 16, List.of(TileTypes.values()), 18);
        CourseImpl course = new CourseImpl(Division.RECREATIONAL, grid, "TestCourse");
        FFARound round = new FFARound(this.plugin, course, false, "TestRound", 1, true);

        round.addTeam(new TeamImpl(player.getUniqueId(), 1));

        Driver driver = new Driver(9, 6, -2, 1, "TestDriver");

        round.start();

        new ThrowMechanicImpl(player.getUniqueId(), plugin, driver, round, "backhand");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return null;
    }
}
