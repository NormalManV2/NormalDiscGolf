package normalmanv2.normalDiscGolf.impl.gui.party;

import normalmanv2.normalDiscGolf.impl.gui.InventoryGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class PartyGUI extends InventoryGUI {


    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 27, ChatColor.GOLD + "Party");
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        super.onClick(event);
        System.out.println("Button: " + this.buttonMap.get(event.getRawSlot()));
    }
}
