package normalmanv2.normalDiscGolf.impl.manager;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public class TaskManager {
    private final Map<String, BukkitTask> activeTasks;

    public TaskManager() {
        this.activeTasks = new HashMap<>();
    }

    public void registerTask(String taskId, BukkitTask task) {
        this.activeTasks.put(taskId, task);
    }

    public void unregisterTask(String taskId) {
        this.activeTasks.remove(taskId);
    }

    public BukkitTask getTask(String taskId) {
        return this.activeTasks.get(taskId);
    }

}
