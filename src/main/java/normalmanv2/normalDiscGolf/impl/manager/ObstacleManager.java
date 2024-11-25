package normalmanv2.normalDiscGolf.impl.manager;

import normalmanv2.normalDiscGolf.impl.course.obstacle.ObstacleImpl;

import java.nio.file.Path;
import java.util.Optional;

public class ObstacleManager {

    private final FileManager fileManager;

    public ObstacleManager(FileManager fileManager) {
        this.fileManager = fileManager;
    }

    public Optional<Path> getFile(ObstacleImpl obstacle) {
        return fileManager.getObstacle(obstacle.getSchematicName());
    }

}
