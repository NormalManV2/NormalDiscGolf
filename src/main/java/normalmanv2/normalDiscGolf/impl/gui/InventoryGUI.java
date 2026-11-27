package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.api.inventory.InventoryHandler;
import normalmanv2.normalDiscGolf.common.button.Button;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuTextures;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

public abstract class InventoryGUI implements InventoryHandler {

    private final MenuSkin skin;
    private Inventory inventory;
    private boolean textured;
    protected final Map<Integer, Button> buttonMap = new HashMap<>();

    protected InventoryGUI(MenuSkin skin) { this.skin = skin; }

    public void prepare(Player player) {
        textured = MenuSkin.available(player);
        inventory = Bukkit.createInventory(null, skin.slots(), skin.title(textured));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Button button = this.buttonMap.get(slot);
        if (button == null) {
            return;
        }
        button.onClick(event);
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        this.populateInventory();
    }

    @Override
    public void onClose(InventoryCloseEvent event) {

    }

    protected void populateInventory() {
        this.buttonMap.forEach((slot, button) -> this.getInventory().setItem(slot, button.getItem()));
        applyTextures();
    }

    public void applyTextures() { if (textured) updateItems(); }

    public void updatePack(Player player) {
        boolean next = MenuSkin.available(player);
        boolean titleChanged = next != textured;
        textured = next;
        refreshForPack(player);
        if (textured || titleChanged) updateItems();
        if (titleChanged && player.getOpenInventory().getTopInventory().equals(inventory))
            player.getOpenInventory().setTitle(skin.title(textured));
    }

    private void updateItems() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            var item = inventory.getItem(slot);
            var updated = MenuTextures.apply(item, textured);
            if (updated != item) inventory.setItem(slot, updated);
        }
    }

    public void addButton(int slot, Button button) {
        this.buttonMap.put(slot, button);
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    public void refreshForPack(Player player) {}

}
