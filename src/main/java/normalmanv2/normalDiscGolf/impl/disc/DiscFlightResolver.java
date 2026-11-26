package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class DiscFlightResolver {
    private DiscFlightResolver() {}
    public static void resolveFlight(Player player, Location location, Vector velocity, boolean goal) {
        GameRound round = findActiveRound(player);
        if (round instanceof RoundImpl impl) impl.play().resolve(player, location, goal);
    }
    public static GameRound findActiveRound(Player player) {
        return NDGManager.getInstance().getRoundHandler().getActiveRounds().stream()
                .filter(round -> round.containsPlayer(player.getUniqueId())).findFirst().orElse(null);
    }
}
