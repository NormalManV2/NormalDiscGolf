package normalmanv2.normalDiscGolf.impl.manager.flight;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DiscFlightManager {

    private static final Map<BukkitTask, ActiveFlight> ACTIVE_FLIGHTS = new HashMap<>();

    private DiscFlightManager() {
    }

    public static void registerFlight(BukkitTask task, UUID playerId, GameRound round, ItemDisplay display) {
        if (task == null || playerId == null || display == null) {
            return;
        }

        ACTIVE_FLIGHTS.put(task, new ActiveFlight(playerId, round == null ? null : round.getId(), display));
    }

    public static void completeFlight(BukkitTask task, Plugin plugin, long displayRemoveDelayTicks) {
        if (task == null) {
            return;
        }

        ActiveFlight flight = ACTIVE_FLIGHTS.remove(task);
        task.cancel();

        if (flight == null || flight.display().isDead()) {
            return;
        }

        if (plugin != null && plugin.isEnabled() && displayRemoveDelayTicks > 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeDisplay(flight.display()), displayRemoveDelayTicks);
            return;
        }

        removeDisplay(flight.display());
    }

    public static void cleanupPlayer(UUID playerId) {
        ACTIVE_FLIGHTS.entrySet()
                .removeIf(entry -> {
                    if (!entry.getValue().playerId().equals(playerId)) {
                        return false;
                    }

                    cancelFlight(entry.getKey(), entry.getValue());
                    return true;
                });
    }

    public static void cleanupRound(String roundId) {
        if (roundId == null) {
            return;
        }

        ACTIVE_FLIGHTS.entrySet()
                .removeIf(entry -> {
                    if (!roundId.equals(entry.getValue().roundId())) {
                        return false;
                    }

                    cancelFlight(entry.getKey(), entry.getValue());
                    return true;
                });
    }

    public static void cleanupAll() {
        for (Map.Entry<BukkitTask, ActiveFlight> entry : ACTIVE_FLIGHTS.entrySet()) {
            cancelFlight(entry.getKey(), entry.getValue());
        }
        ACTIVE_FLIGHTS.clear();
    }

    private static void cancelFlight(BukkitTask task, ActiveFlight flight) {
        task.cancel();
        removeDisplay(flight.display());
    }

    private static void removeDisplay(ItemDisplay display) {
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    private record ActiveFlight(UUID playerId, String roundId, ItemDisplay display) {
    }
}
