package normalmanv2.normalDiscGolf.impl.gui.round;

import normalmanv2.normalDiscGolf.common.button.Button;
import normalmanv2.normalDiscGolf.common.item.ItemBuilder;
import normalmanv2.normalDiscGolf.impl.gui.InventoryGUI;
import normalmanv2.normalDiscGolf.impl.gui.round.button.DoublesButton;
import normalmanv2.normalDiscGolf.impl.gui.round.button.FFAButton;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class RoundPickerGUI extends InventoryGUI {

    public RoundPickerGUI() {
        super();
        this.addButton(11, new FFAButton(new ItemBuilder(Material.EMERALD)
                .setDisplayName("&aFFA")
                .setLore("&7Queue for a free-for-all round.")
                .build()));
        this.addButton(15, new DoublesButton(new ItemBuilder(Material.DIAMOND)
                .setDisplayName("&bDoubles")
                .setLore("&7Queue for a two-player team round.")
                .build()));
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 3 * 9, ChatColor.translateAlternateColorCodes('&', "&4 &lRound Picker"));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        System.out.println("Round Picker GUI Clicked");
        int slot = event.getRawSlot();
        Button button = this.buttonMap.get(slot);
        if (button == null) {
            System.out.println("Button is null");
            return;
        }
        button.onClick(event);
    }
}
