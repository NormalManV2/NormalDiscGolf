package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.impl.manager.gui.GuiManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

public class GuiListener implements Listener {

    private final GuiManager guiManager;

    public GuiListener(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!this.guiManager.manages(event.getInventory())) return;
        this.guiManager.handleClick(event);
        event.setCancelled(true);
    }

    @EventHandler public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (this.guiManager.manages(event.getInventory())) event.setCancelled(true);
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {

        this.guiManager.handleOpen(event);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {

        this.guiManager.handleClose(event);
    }

}
