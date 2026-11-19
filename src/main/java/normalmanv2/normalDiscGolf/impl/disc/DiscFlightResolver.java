package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.event.GoalScoreEvent;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.round.RoundPresentation;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

public final class DiscFlightResolver {

    private static final int HAZARD_PENALTY_STROKES = 1;
    private static final double LIE_SETBACK_BLOCKS = 1.5;

    private DiscFlightResolver() {
    }

    public static void resolveFlight(Player player, Location currentLocation, Vector initialVelocity, boolean goalScored) {
        GameRound round = findActiveRound(player);
        if (round != null && goalScored) {
            Bukkit.getPluginManager().callEvent(new GoalScoreEvent(player, round.getCurrentHole(), round));
        } else if (!goalScored) {
            Location lieLocation = resolveLieLocation(player, round, currentLocation, initialVelocity);
            if (round != null) {
                RoundPresentation.teleportTeamToLie(player, round, lieLocation);
            } else {
                player.teleport(lieLocation);
            }
        }

        if (round != null && !round.isOver()) {
            round.nextTurn();
        }
    }

    private static Location resolveLieLocation(Player player, GameRound round, Location currentLocation, Vector initialVelocity) {
        Location normalLie = currentLocation.clone().subtract(getThrowDirection(player, initialVelocity).multiply(LIE_SETBACK_BLOCKS));
        if (!(round != null && round.getCourse() instanceof CourseImpl course)) {
            return normalLie;
        }

        TileTypes landingType = course.grid().getTileTypeAtLocation(currentLocation);
        if (landingType == TileTypes.WATER || landingType == TileTypes.OUT_OF_BOUNDS) {
            applyHazardPenalty(round, player, landingType);
            return player.getLocation().clone();
        }

        return normalLie;
    }

    private static Vector getThrowDirection(Player player, Vector initialVelocity) {
        if (initialVelocity.lengthSquared() > 0) {
            return initialVelocity.clone().normalize();
        }

        return player.getEyeLocation().getDirection().normalize();
    }

    private static void applyHazardPenalty(GameRound round, Player thrower, TileTypes landingType) {
        ScoreCard scoreCard = round.getScoreCardById(thrower.getUniqueId());
        if (scoreCard != null) {
            scoreCard.addPenalty(HAZARD_PENALTY_STROKES);
        }

        sendTeamMessage(
                round,
                thrower.getUniqueId(),
                ChatColor.RED
                        + formatHazardName(landingType)
                        + ". One penalty stroke. Throw again from the previous lie.");
    }

    private static String formatHazardName(TileTypes landingType) {
        return landingType == TileTypes.WATER ? "Water hazard" : "Out of bounds";
    }

    private static void sendTeamMessage(GameRound round, UUID playerId, String message) {
        Team team = findTeam(round, playerId);
        if (team == null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
            return;
        }

        for (UUID teamMemberId : team.getTeamMembers()) {
            Player player = Bukkit.getPlayer(teamMemberId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private static Team findTeam(GameRound round, UUID playerId) {
        for (Team team : round.getTeams()) {
            if (team.contains(playerId)) {
                return team;
            }
        }

        return null;
    }

    public static GameRound findActiveRound(Player player) {
        return NDGManager.getInstance().getRoundHandler().getActiveRounds().stream()
                .filter(round -> round.containsPlayer(player.getUniqueId()))
                .findFirst()
                .orElse(null);
    }
}
