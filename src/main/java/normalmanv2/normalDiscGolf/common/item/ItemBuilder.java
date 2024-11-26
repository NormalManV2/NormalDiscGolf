package normalmanv2.normalDiscGolf.common.item;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;
    private final PersistentDataContainer container;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
        if (this.meta == null) {
            throw new NullPointerException("ItemMeta cannot be null! " + material);
        }
        this.container = meta.getPersistentDataContainer();
    }

    public ItemBuilder setDisplayName(String displayName) {
        this.meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
        return this;
    }

    public ItemBuilder setLore(String... lore) {
        if (lore == null || lore.length == 0) {
            this.meta.setLore(null);
        } else {
            List<String> loreList = new ArrayList<>();
            for (String string : lore) {
                loreList.add(ChatColor.translateAlternateColorCodes('&', string));
            }
            this.meta.setLore(loreList);
        }
        return this;
    }

    public <T, Z> ItemBuilder addPDCData(NamespacedKey key, PersistentDataType<T, Z> dataType, Z value) {
        this.container.set(key, dataType, value);
        return this;
    }

    public ItemBuilder addLoreToPDC(NamespacedKey key) {
        List<String> loreList = meta.getLore();
        if (loreList != null) {
            String loreString = String.join(";", loreList);
            this.container.set(key, PersistentDataType.STRING, loreString);
        }
        return this;
    }

    public ItemStack build() {
        this.item.setItemMeta(this.meta);
        return this.item.clone();
    }

}
