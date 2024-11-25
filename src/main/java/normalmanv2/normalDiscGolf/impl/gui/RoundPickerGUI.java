package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.common.button.Button;
import normalmanv2.normalDiscGolf.impl.event.PlayerRoundQueueEvent;
import normalmanv2.normalDiscGolf.impl.manager.RoundHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RoundPickerGUI extends InventoryGUI {

    private final RoundHandler roundHandler;

    public RoundPickerGUI(RoundHandler roundHandler) {
        this.roundHandler = roundHandler;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 3 * 9, ChatColor.translateAlternateColorCodes('&', "&4 &lRound Picker"));
    }

    @Override
    public void populateInventory() {
        ItemStack ffa = new ItemStack(Material.EMERALD);
        ItemMeta ffaMeta = ffa.getItemMeta();
        ffaMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&4 &lFFA Round"));
        ffa.setItemMeta(ffaMeta);

        Button ffaRoundButton = new Button(ffa) {
            @Override
            public void onClick(InventoryClickEvent event) {
                Bukkit.getPluginManager().callEvent(new PlayerRoundQueueEvent(event.getWhoClicked().getUniqueId(), roundHandler.getActiveRounds(), "ffa"));
            }
        };

        this.addButton(11, ffaRoundButton);
        super.populateInventory();
    }
}
