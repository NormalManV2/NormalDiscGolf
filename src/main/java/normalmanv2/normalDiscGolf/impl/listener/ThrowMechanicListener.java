package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.impl.event.DiscThrowEvent;
import normalmanv2.normalDiscGolf.impl.event.ThrowMechanicEvent;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.manager.flight.DiscFlightManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class ThrowMechanicListener implements Listener {

    private final Map<UUID, ThrowMechanicImpl> activeMechanics = new HashMap<>();
    private final Set<PlayerInteractEvent> releases = Collections.newSetFromMap(new WeakHashMap<>());
    public boolean released(PlayerInteractEvent event) { return releases.contains(event); }

    @EventHandler
    public void onThrowMechanic(ThrowMechanicEvent event) {
        track((ThrowMechanicImpl) event.getMechanic());
    }

    public void track(ThrowMechanicImpl mechanic) {
        ThrowMechanicImpl previousMechanic = this.activeMechanics.put(mechanic.getPlayerId(), mechanic);

        if (previousMechanic != null) {
            previousMechanic.dispose();
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL || event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        ThrowMechanicImpl mechanic = this.activeMechanics.get(playerId);
        if (mechanic == null) {
            return;
        }
        if (mechanic.isDisposed()) {
            this.activeMechanics.remove(playerId);
            return;
        }

        releases.add(event);
        event.setCancelled(true);
        if (!mechanic.click(event.getPlayer())) return;
        this.activeMechanics.remove(playerId);
        mechanic.dispose();

        if (mechanic.isPractice()) NDGManager.getInstance().getClubhouse().release(event.getPlayer(), mechanic);
        else Bukkit.getPluginManager().callEvent(new DiscThrowEvent(mechanic));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.cancelMechanic(event.getPlayer().getUniqueId());
        DiscFlightManager.cleanupPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        ThrowMechanicImpl mechanic = activeMechanics.get(event.getPlayer().getUniqueId());
        if (mechanic == null || mechanic.isDisposed()) {
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
        activeMechanics.values().forEach(ThrowMechanicImpl::dispose);
        activeMechanics.clear();
    }

    public boolean cancelMechanic(UUID playerId) {
        ThrowMechanicImpl mechanic = activeMechanics.remove(playerId);
        if (mechanic == null || mechanic.isDisposed()) return false;
        mechanic.dispose();
        return true;
    }
}
