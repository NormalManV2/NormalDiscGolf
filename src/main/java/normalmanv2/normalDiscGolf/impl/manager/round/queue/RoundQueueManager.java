package normalmanv2.normalDiscGolf.impl.manager.round.queue;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.delegate.lifecycle.DelegateRoundLifecycle;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundScoreCardManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundStrokeManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundTeamManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundTurnManager;
import normalmanv2.normalDiscGolf.api.round.delegate.settings.DelegateRoundSettings;
import normalmanv2.normalDiscGolf.api.round.settings.RoundType;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.round.*;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.CourseCreator;
import normalmanv2.normalDiscGolf.impl.course.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.manager.round.lifecycle.RoundHandler;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.team.TeamImpl;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class RoundQueueManager {

    private final RoundHandler roundHandler;
    private final Set<GameRound> queuedRounds;
    private final Map<Division, Map<UUID, String>> playerQueue;
    private final PlayerDataManager playerDataManager;

    public RoundQueueManager(RoundHandler roundHandler, PlayerDataManager playerDataManager) {
        this.roundHandler = roundHandler;
        this.playerDataManager = playerDataManager;
        this.queuedRounds = new HashSet<>();
        this.playerQueue = new HashMap<>();
        for (Division division : Division.values()) {
            this.playerQueue.put(division, new HashMap<>());
        }
    }

    public void addPlayerToQueue(UUID player, String roundFormat) {
        PlayerData playerData = NDGManager.getInstance().getPlayerDataManager().getDataByPlayer(player);
        Division division = playerData.getRating().getDivision();

        this.playerQueue.get(division).put(player, roundFormat);
    }

    public void removePlayerFromQueue(UUID player) {
        PlayerData playerData = playerDataManager.getDataByPlayer(player);
        Division division = playerData.getRating().getDivision();
        this.playerQueue.get(division).remove(player);
    }

    public boolean isPlayerQueued(UUID player) {
        PlayerData playerData = playerDataManager.getDataByPlayer(player);
        Division division = playerData.getRating().getDivision();
        return this.playerQueue.get(division).containsKey(player);
    }

    public void addRoundToQueue(GameRound round) {
        this.queuedRounds.add(round);
    }

    public void removeRoundFromQueue(GameRound round) {
        this.queuedRounds.remove(round);
    }

    public boolean isRoundQueued(GameRound round) {
        return this.queuedRounds.contains(round);
    }

    public Map<UUID, String> getPlayerQueue(Division division) {
        return this.playerQueue.containsKey(division)
                ? Collections.unmodifiableMap(this.playerQueue.get(division))
                : Collections.emptyMap();
    }

    public Set<GameRound> getQueuedRounds() {
        return Collections.unmodifiableSet(this.queuedRounds);
    }

    public void tickPlayerQueue() {
        for (Division division : Division.values()) {
            Map<UUID, String> divisionQueue = this.playerQueue.get(division);
            if (divisionQueue.isEmpty()) continue;

            Iterator<UUID> iterator = divisionQueue.keySet().iterator();

            while (iterator.hasNext()) {
                UUID playerId = iterator.next();
                Player player = Bukkit.getPlayer(playerId);

                if (player == null) continue;

                String roundFormat = divisionQueue.get(playerId);

                DefaultRoundSettings settings = (DefaultRoundSettings) DelegateRoundSettings.create(Constants.FFA_ROUND_MAX_PLAYERS, RoundType.RECREATIONAL, division, false);

                if (roundFormat.equalsIgnoreCase("DOUBLES")) {
                    settings = (DefaultRoundSettings) DelegateRoundSettings.create(Constants.DOUBLES_ROUND_MAX_TEAMS, RoundType.RECREATIONAL, division, false);
                }

                DefaultRoundTurnManager turnManager = (DefaultRoundTurnManager) DelegateRoundTurnManager.createDefault();
                DefaultRoundTeamManager teamManager = (DefaultRoundTeamManager) DelegateRoundTeamManager.createDefault();
                DefaultRoundLifecycle lifecycle = (DefaultRoundLifecycle) DelegateRoundLifecycle.createDefault();
                DefaultRoundStrokeManager strokeManager = (DefaultRoundStrokeManager) DelegateRoundStrokeManager.createDefault();
                DefaultRoundScoreCardManager scoreCardManager = (DefaultRoundScoreCardManager) DelegateRoundScoreCardManager.createDefault();

                GameRound round = findOrCreateRoundForDivision(
                        roundFormat,
                        RoundType.RECREATIONAL,
                        new CourseCreator().create(
                                division,
                                new CourseGrid(
                                        16,
                                        16,
                                        Arrays.stream(TileTypes.values()).toList(),
                                        18),
                                playerId + "." + roundFormat),
                        player.getDisplayName(),
                        settings,
                        turnManager,
                        teamManager,
                        lifecycle,
                        strokeManager,
                        scoreCardManager);

                if (round == null)
                    throw new RuntimeException("Error ticking player queue: No suitable round could be found!");
                if (this.roundHandler.getActiveRounds().contains(round)) continue;

                if (round instanceof FFARound) {
                    Team team = new TeamImpl(playerId, 1);
                    round.addTeam(team);
                    iterator.remove();
                    return;
                }

                for (Team team : round.getTeams()) {
                    if (team.isFull()) continue;
                    team.addPlayer(playerId);
                    iterator.remove();
                }
            }
            divisionQueue.clear();
        }
    }

    public void tickRoundQueue() {
        for (GameRound round : this.queuedRounds) {

            if (round.isPrivate()) {
                round.start();
                this.queuedRounds.remove(round);
                return;
            }

            if (round.getTeams().stream().allMatch(Team::isFull) && round.isFull()) {
                round.start();
                this.queuedRounds.remove(round);
                return;
            }
        }
    }

    private GameRound findOrCreateRoundForDivision(
            String roundFormat,
            RoundType type,
            CourseImpl course,
            String courseName,
            DefaultRoundSettings settings,
            DefaultRoundTurnManager turnManager,
            DefaultRoundTeamManager teamManager,
            DefaultRoundLifecycle lifecycle,
            DefaultRoundStrokeManager strokeManager,
            DefaultRoundScoreCardManager scoreCardManager) {
        return this.queuedRounds.stream()
                .filter(round -> round.getId().equalsIgnoreCase(roundFormat) && round.type() == type)
                .findFirst()
                .orElseGet(() -> {
                    GameRound newRound = this.roundHandler.createRound(roundFormat, course, courseName, settings, turnManager, teamManager, lifecycle, strokeManager, scoreCardManager);
                    this.addRoundToQueue(newRound);
                    return newRound;
                });
    }
}
