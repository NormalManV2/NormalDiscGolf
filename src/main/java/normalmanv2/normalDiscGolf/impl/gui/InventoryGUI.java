package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.api.inventory.InventoryHandler;
import normalmanv2.normalDiscGolf.common.button.Button;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

public abstract class InventoryGUI implements InventoryHandler {

    private final Inventory inventory;
    private final Map<Integer, Button> buttonMap;

    public InventoryGUI() {
        this.inventory = this.createInventory();
        this.buttonMap = new HashMap<>();
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();
        Button button = this.buttonMap.get(slot);
        if (button == null) return;

    }

    @Override
    public void onOpen(InventoryOpenEvent event) {

    }

    @Override
    public void onClose(InventoryCloseEvent event) {

    }

    protected abstract Inventory createInventory();

    public void addButton(int slot, Button button) {
        this.buttonMap.put(slot, button);
    }

    public Inventory getInventory() {
        return this.inventory;
    }

}
