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
        gui.prepare(player);
        this.registerInventory(gui.getInventory(), gui);
        if (player.openInventory(gui.getInventory()) == null) this.unregisterInventory(gui.getInventory());

    }

    public boolean manages(Inventory inventory) { return this.inventories.containsKey(inventory); }

    public void refreshForPack(Player player) {
        if (inventories.get(player.getOpenInventory().getTopInventory()) instanceof InventoryGUI gui) {
            gui.updatePack(player);
        }
    }

    public void handleClick(InventoryClickEvent event) {
        InventoryHandler handler = this.inventories.get(event.getInventory());
        if (handler == null) {

            return;
        }
        handler.onClick(event);
        if (handler instanceof InventoryGUI gui) gui.applyTextures();
    }

    public void handleOpen(InventoryOpenEvent event) {
        InventoryHandler handler = this.inventories.get(event.getInventory());
        if (handler == null) return;
        handler.onOpen(event);
        if (handler instanceof InventoryGUI gui) gui.applyTextures();
    }

    public void handleClose(InventoryCloseEvent event) {
        InventoryHandler handler = this.inventories.get(event.getInventory());
        if (handler == null) return;
        handler.onClose(event);
        this.unregisterInventory(event.getInventory());
    }


}
