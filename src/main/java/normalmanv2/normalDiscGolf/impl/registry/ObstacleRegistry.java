package normalmanv2.normalDiscGolf.impl.registry;

import normalmanv2.normalDiscGolf.impl.course.obstacle.ObstacleImpl;
import normalmanv2.normalDiscGolf.impl.course.obstacle.obstacles.Bush;
import normalmanv2.normalDiscGolf.impl.course.obstacle.obstacles.Pin;
import normalmanv2.normalDiscGolf.impl.course.obstacle.obstacles.Rock;
import normalmanv2.normalDiscGolf.impl.course.obstacle.obstacles.Tree;
import org.normal.impl.registry.RegistryImpl;

public class ObstacleRegistry extends RegistryImpl<String, ObstacleImpl> {

    public ObstacleRegistry() {
        this.register("tree", new Tree(null, "tree"));
        this.register("bigTree", new Tree(null, "BigTree"));
        this.register("tallTree", new Tree(null, "TallTree"));
        this.register("tallTree1", new Tree(null, "TallTree1"));
        this.register("bush", new Bush(null, "bush"));
        this.register("rock", new Rock(null, "rock"));
        this.register("pin", new Pin(null, "pin"));
    }

}
