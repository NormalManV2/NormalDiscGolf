package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.common.item.ItemBuilder;
import normalmanv2.normalDiscGolf.impl.gui.InventoryGUI;
import normalmanv2.normalDiscGolf.impl.gui.RoundPickerGUI;
import normalmanv2.normalDiscGolf.impl.gui.button.FFAButton;
import normalmanv2.normalDiscGolf.impl.manager.GuiManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RoundQueueTest implements CommandExecutor {

    private final GuiManager guiManager;

    public RoundQueueTest(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (!(commandSender instanceof Player player)) return false;

        InventoryGUI gui = new RoundPickerGUI();
        gui.addButton(11, new FFAButton(new ItemBuilder(Material.EMERALD).setDisplayName("&4 &lFFA Round").build()));

        this.guiManager.openGUI(gui, player);

        return true;
    }
}
