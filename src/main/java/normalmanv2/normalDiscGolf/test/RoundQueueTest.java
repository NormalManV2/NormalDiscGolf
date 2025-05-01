package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.gui.InventoryGUI;
import normalmanv2.normalDiscGolf.impl.gui.round.RoundPickerGUI;
import normalmanv2.normalDiscGolf.impl.gui.round.button.FFAButton;
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

    private final GuiManager guiManager = NDGManager.getInstance().getGuiManager();

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (!(commandSender instanceof Player player)) return false;

        InventoryGUI gui = new RoundPickerGUI();

        this.guiManager.openGUI(gui, player);

        return true;
    }
}
