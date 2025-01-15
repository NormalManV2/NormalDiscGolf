package normalmanv2.normalDiscGolf.impl.manager.course;

import normalmanv2.normalDiscGolf.impl.course.obstacle.ObstacleImpl;
import normalmanv2.normalDiscGolf.impl.manager.file.FileManager;

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
