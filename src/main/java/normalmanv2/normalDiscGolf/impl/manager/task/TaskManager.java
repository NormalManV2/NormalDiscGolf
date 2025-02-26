package normalmanv2.normalDiscGolf.impl.manager.task;

import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TaskManager {
    private static final Map<BukkitTask, UUID> ACTIVE_TASKS = new HashMap<>();

    public static void registerTask(BukkitTask task, UUID playerId) {
        ACTIVE_TASKS.put(task, playerId);
    }

    public static void unregisterTask(BukkitTask task) {
        ACTIVE_TASKS.remove(task);
    }

    public static UUID getUserTask(BukkitTask task) {
        return ACTIVE_TASKS.get(task);
    }

    public static Map<BukkitTask, UUID> getActiveTasks() {
        return Collections.unmodifiableMap(ACTIVE_TASKS);
    }

}
