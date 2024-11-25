package normalmanv2.normalDiscGolf.impl.manager;

import normalmanv2.normalDiscGolf.api.inventory.InventoryHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

public class GuiManager {

    private final Map<Inventory, InventoryHandler> inventories;

    public GuiManager() {
        this.inventories = new HashMap<>();
    }

    public void registerInventory(Inventory inventory, InventoryHandler inventoryHandler) {
        this.inventories.put(inventory, inventoryHandler);
    }

    public void unregisterInventory(Inventory inventory) {
        this.inventories.remove(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        InventoryHandler handler = this.inventories.get(event.getInventory());
        if (handler == null) return;
        handler.onClick(event);
    }

    public void handleOpen(InventoryOpenEvent event) {
        InventoryHandler handler = this.inventories.get(event.getInventory());
        if (handler == null) return;
        handler.onOpen(event);
    }

    public void handleClose(InventoryCloseEvent event) {
        InventoryHandler handler = this.inventories.get(event.getInventory());
        if (handler == null) return;
        handler.onClose(event);
        this.unregisterInventory(event.getInventory());
    }

}
