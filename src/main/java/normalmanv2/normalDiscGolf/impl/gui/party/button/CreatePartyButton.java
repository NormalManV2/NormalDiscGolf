package normalmanv2.normalDiscGolf.impl.gui.party.button;

import normalmanv2.normalDiscGolf.common.button.Button;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.team.TeamImpl;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class CreatePartyButton extends Button {

    public CreatePartyButton(ItemStack item) {
        super(item);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        TeamImpl team = new TeamImpl(event.getWhoClicked().getUniqueId(), Constants.MAXIMUM_PARTY_MEMBERS);
        NDGManager.getInstance().getPartyManager().addParty(team);
    }
}
