package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.impl.event.DiscThrowEvent;
import normalmanv2.normalDiscGolf.impl.event.ThrowMechanicEvent;
import normalmanv2.normalDiscGolf.impl.manager.flight.DiscFlightManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ThrowMechanicListener implements Listener {

    private final Map<UUID, ThrowMechanicImpl> activeMechanics;
    private final java.util.Set<PlayerInteractEvent> releases = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public boolean released(PlayerInteractEvent event) { return releases.contains(event); }

    public ThrowMechanicListener() {
        this.activeMechanics = new HashMap<>();
    }

    @EventHandler
    public void onThrowMechanic(ThrowMechanicEvent event) {
        ThrowMechanicImpl mechanic = (ThrowMechanicImpl) event.getMechanic();
        ThrowMechanicImpl previousMechanic = this.activeMechanics.put(event.getPlayer().getUniqueId(), mechanic);

        if (previousMechanic != null) {
            previousMechanic.dispose();
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            return;
        }

        ThrowMechanicImpl mechanic = this.activeMechanics.remove(event.getPlayer().getUniqueId());
        if (mechanic == null) {
            return;
        }

        releases.add(event);
        event.setCancelled(true);
        int lockedPower = (int) Math.round(mechanic.getPower().get() * 100);
        event.getPlayer().sendMessage(ChatColor.GRAY + "Power locked: " + lockedPower + "%");
        mechanic.dispose();

        Bukkit.getPluginManager().callEvent(new DiscThrowEvent(mechanic));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.cancelMechanic(event.getPlayer().getUniqueId());
        DiscFlightManager.cleanupPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!this.activeMechanics.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setCancelled(true);
        }
    }

    public void cancelAll() {
        for (UUID playerId : new ArrayList<>(this.activeMechanics.keySet())) {
            this.cancelMechanic(playerId);
        }
    }

    public void cancelMechanic(UUID playerId) {
        ThrowMechanicImpl mechanic = this.activeMechanics.remove(playerId);
        if (mechanic != null) {
            mechanic.dispose();
        }
    }
}
