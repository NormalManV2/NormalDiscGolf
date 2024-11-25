package normalmanv2.normalDiscGolf.impl.registry;

import normalmanv2.normalDiscGolf.impl.course.obstacle.ObstacleImpl;

import java.util.HashMap;
import java.util.Map;

public class ObstacleRegistry {

    private final Map<String, ObstacleImpl> obstacleMap;

    public ObstacleRegistry() {
        this.obstacleMap = new HashMap<>();
    }

    public void registerObstacle(String obstacleName, ObstacleImpl obstacle) {
        this.obstacleMap.put(obstacleName, obstacle);
    }

    public ObstacleImpl getObstacle(String obstacleName) {
        return this.obstacleMap.get(obstacleName).clone();
    }

}
