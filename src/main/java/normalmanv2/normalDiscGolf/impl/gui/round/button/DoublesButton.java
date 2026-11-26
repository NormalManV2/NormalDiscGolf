package normalmanv2.normalDiscGolf.impl.gui.round.button;

import normalmanv2.normalDiscGolf.common.button.Button;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.event.PlayerQueueRoundEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class DoublesButton extends Button {

    public DoublesButton(ItemStack item) {
        super(item);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) player.performCommand("ndg queue doubles");
        event.getWhoClicked().closeInventory();
    }
}
