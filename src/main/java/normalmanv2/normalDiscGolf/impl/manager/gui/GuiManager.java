package normalmanv2.normalDiscGolf.impl.manager.gui;

import normalmanv2.normalDiscGolf.api.inventory.InventoryHandler;
import normalmanv2.normalDiscGolf.impl.gui.InventoryGUI;
import org.bukkit.entity.Player;
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

    public void openGUI(InventoryGUI gui, Player player) {
        this.registerInventory(gui.getInventory(), gui);
        player.openInventory(gui.getInventory());
        System.out.println("GUI opened " + gui);
    }

    public void handleClick(InventoryClickEvent event) {
        InventoryHandler handler = this.inventories.get(event.getInventory());
        if (handler == null) {
            System.out.println("Handler is null");
            return;
        }
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
