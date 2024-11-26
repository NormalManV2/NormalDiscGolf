package normalmanv2.normalDiscGolf.common.button;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public abstract class Button {

    private final ItemStack item;

    public Button(ItemStack item) {
        this.item = item;
    }

    public ItemStack getItem() {
        return this.item;
    }

    public abstract void onClick(InventoryClickEvent event);
}
