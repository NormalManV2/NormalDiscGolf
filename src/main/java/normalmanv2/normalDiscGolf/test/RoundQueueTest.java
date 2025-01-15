package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.impl.gui.InventoryGUI;
import normalmanv2.normalDiscGolf.impl.gui.RoundPickerGUI;
import normalmanv2.normalDiscGolf.impl.gui.button.FFAButton;
import normalmanv2.normalDiscGolf.impl.manager.gui.GuiManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RoundQueueTest implements CommandExecutor {

    private final GuiManager guiManager;
    private static final ItemStack FFA_BUTTON = new ItemStack(Material.EMERALD);

    public RoundQueueTest(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (!(commandSender instanceof Player player)) return false;

        ItemMeta itemMeta = FFA_BUTTON.getItemMeta();
        itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&4FFA ROUND"));
        FFA_BUTTON.setItemMeta(itemMeta);

        InventoryGUI gui = new RoundPickerGUI();
        gui.addButton(11, new FFAButton(FFA_BUTTON));

        this.guiManager.openGUI(gui, player);

        return true;
    }
}
