package normalmanv2.normalDiscGolf.impl.gui.round.button;

import normalmanv2.normalDiscGolf.common.button.Button;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.event.PlayerQueueRoundEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class FFAButton extends Button {

    public FFAButton(ItemStack item) {
        super(item);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) player.performCommand("ndg queue ffa");
        event.getWhoClicked().closeInventory();
    }
}
