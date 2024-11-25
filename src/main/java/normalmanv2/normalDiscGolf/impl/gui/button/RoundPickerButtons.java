package normalmanv2.normalDiscGolf.impl.gui.button;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RoundPickerButtons {

    private static final ItemStack ffaRound;

    static {
        ffaRound = new ItemStack(Material.EMERALD);
        ItemMeta ffaRoundMeta = ffaRound.getItemMeta();
        ffaRoundMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "FFA Round"));
        ffaRound.setItemMeta(ffaRoundMeta);
    }

    public static ItemStack getFfaRound() {
        return ffaRound;
    }


}
