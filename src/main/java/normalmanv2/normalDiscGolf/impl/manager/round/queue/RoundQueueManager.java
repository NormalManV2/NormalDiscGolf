package normalmanv2.normalDiscGolf.impl.manager.round.queue;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.delegate.settings.DelegateRoundSettings;
import normalmanv2.normalDiscGolf.api.round.settings.RoundType;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.round.*;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.creator.CourseCreator;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.manager.round.lifecycle.RoundHandler;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.round.DoublesRound;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.team.TeamImpl;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;

public class RoundQueueManager {

    public enum QueueResult {
        SUCCESS,
        INVALID_FORMAT,
        REQUIRED_ASSETS_MISSING,
        PLAYER_DATA_MISSING,
        PLAYER_OFFLINE,
        PLAYER_IN_ACTIVE_ROUND,
        PLAYER_ALREADY_QUEUED,
        DIVISION_MISMATCH,
        PARTY_TOO_LARGE,
        ROUND_FULL
    }

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

    public boolean addPlayerToQueue(UUID player, String roundFormat) {
        String normalizedFormat = normalizeRoundFormat(roundFormat);
        if (normalizedFormat == null
                || this.isPlayerInActiveRound(player)
                || !NDGManager.getInstance().getFileManager().areRequiredObstaclesAvailable()) {
            return false;
        }

        PlayerData playerData = this.playerDataManager.getDataByPlayer(player);
        if (playerData == null) {
            return false;
        }

        Optional<String> queuedFormat = this.getQueuedRoundFormat(player);
        if (queuedFormat.isPresent() && queuedFormat.get().equalsIgnoreCase(normalizedFormat)) {
            return false;
        }

        this.removePlayerFromQueue(player);
        Division division = playerData.getRating().getDivision();
        this.playerQueue.get(division).put(player, normalizedFormat);
        return true;
    }

    public QueueResult addPartyToQueue(Collection<UUID> players, String roundFormat) {
        String normalizedFormat = normalizeRoundFormat(roundFormat);
        if (normalizedFormat == null) {
            return QueueResult.INVALID_FORMAT;
        }

        if (!NDGManager.getInstance().getFileManager().areRequiredObstaclesAvailable()) {
            return QueueResult.REQUIRED_ASSETS_MISSING;
        }

        List<UUID> playerIds = players.stream().distinct().toList();
        if (playerIds.isEmpty()) {
            return QueueResult.PLAYER_DATA_MISSING;
        }

        if (normalizedFormat.equalsIgnoreCase("DOUBLES") && playerIds.size() > 2) {
            return QueueResult.PARTY_TOO_LARGE;
        }

        if (normalizedFormat.equalsIgnoreCase("FFA") && playerIds.size() > Constants.FFA_ROUND_MAX_PLAYERS) {
            return QueueResult.PARTY_TOO_LARGE;
        }

        Division division = null;
        for (UUID playerId : playerIds) {
            if (Bukkit.getPlayer(playerId) == null) {
                return QueueResult.PLAYER_OFFLINE;
            }

            if (this.isPlayerInActiveRound(playerId)) {
                return QueueResult.PLAYER_IN_ACTIVE_ROUND;
            }

            if (this.isPlayerQueued(playerId)) {
                return QueueResult.PLAYER_ALREADY_QUEUED;
            }

            PlayerData playerData = this.playerDataManager.getDataByPlayer(playerId);
            if (playerData == null) {
                return QueueResult.PLAYER_DATA_MISSING;
            }

            Division playerDivision = playerData.getRating().getDivision();
            if (division == null) {
                division = playerDivision;
            } else if (division != playerDivision) {
                return QueueResult.DIVISION_MISMATCH;
            }
        }

        DefaultRoundSettings settings = this.createSettings(normalizedFormat, division);
        GameRound round = this.findOrCreateRoundForParty(
                normalizedFormat,
                RoundType.RECREATIONAL,
                division,
                settings,
                playerIds.size());

        if (round == null || !this.addPartyToQueuedRound(round, playerIds, normalizedFormat)) {
            return QueueResult.ROUND_FULL;
        }

        for (UUID playerId : playerIds) {
            this.removePlayerFromQueue(playerId);
        }

        return QueueResult.SUCCESS;
    }

    public boolean removePlayerFromQueue(UUID player) {
        boolean removed = false;
        for (Map<UUID, String> divisionQueue : this.playerQueue.values()) {
            removed |= divisionQueue.remove(player) != null;
        }
        removed |= this.removePlayerFromQueuedRounds(player);
        return removed;
    }

    public boolean isPlayerQueued(UUID player) {
        return this.playerQueue.values().stream().anyMatch(queue -> queue.containsKey(player))
                || this.queuedRounds.stream().anyMatch(round -> round.containsPlayer(player));
    }

    public Optional<String> getQueuedRoundFormat(UUID player) {
        Optional<String> queuedFormat = this.playerQueue.values()
                .stream()
                .filter(queue -> queue.containsKey(player))
                .map(queue -> queue.get(player))
                .findFirst();

        if (queuedFormat.isPresent()) {
            return queuedFormat;
        }

        return this.queuedRounds
                .stream()
                .filter(round -> round.containsPlayer(player))
                .map(this::formatForRound)
                .findFirst();
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

            Iterator<Map.Entry<UUID, String>> iterator = divisionQueue.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<UUID, String> queuedPlayer = iterator.next();
                UUID playerId = queuedPlayer.getKey();
                Player player = Bukkit.getPlayer(playerId);

                if (player == null) continue;

                if (this.isPlayerInActiveRound(playerId)) {
                    iterator.remove();
                    continue;
                }

                String roundFormat = queuedPlayer.getValue();
                DefaultRoundSettings settings = this.createSettings(roundFormat, division);
                GameRound round = findOrCreateRoundForDivision(
                        roundFormat,
                        RoundType.RECREATIONAL,
                        division,
                        settings);

                if (round == null)
                    throw new RuntimeException("Error ticking player queue: No suitable round could be found!");
                if (this.roundHandler.getActiveRounds().contains(round)) continue;

                if (this.addPlayerToQueuedRound(round, playerId, roundFormat)) {
                    iterator.remove();
                    player.sendMessage(ChatColor.GREEN + "You joined a " + displayRoundFormat(roundFormat) + " round.");
                }
            }
        }
    }

    public void tickRoundQueue() {
        Iterator<GameRound> iterator = this.queuedRounds.iterator();
        while (iterator.hasNext()) {
            GameRound round = iterator.next();

            if (!this.shouldStartRound(round)) {
                continue;
            }

            iterator.remove();
            this.roundHandler.startRound(round);
            return;
        }
    }

    private boolean shouldStartRound(GameRound round) {
        if (round.isPrivate()) {
            return true;
        }

        if (round instanceof FFARound) {
            return !round.getTeams().isEmpty();
        }

        if (round instanceof DoublesRound) {
            return round.getTeams().size() >= Constants.DOUBLES_ROUND_MIN_TEAMS
                    && round.getTeams().stream().allMatch(Team::isFull);
        }

        return !round.getTeams().isEmpty() && round.getTeams().stream().allMatch(Team::isFull);
    }

    private boolean removePlayerFromQueuedRounds(UUID playerId) {
        boolean removed = false;
        Iterator<GameRound> roundIterator = this.queuedRounds.iterator();
        while (roundIterator.hasNext()) {
            GameRound round = roundIterator.next();
            List<Team> teams = round.getTeams();

            for (Team team : teams) {
                if (!team.contains(playerId)) {
                    continue;
                }

                team.removePlayer(playerId);
                if (team.getTeamMembers().isEmpty()) {
                    round.removeTeam(team);
                }
                removed = true;
                break;
            }

            if (round.getTeams().isEmpty()) {
                roundIterator.remove();
                round.dispose();
            }
        }

        return removed;
    }

    private GameRound findOrCreateRoundForDivision(
            String roundFormat,
            RoundType type,
            Division division,
            DefaultRoundSettings settings) {
        return this.queuedRounds.stream()
                .filter(round -> this.matchesQueuedRound(round, roundFormat, type, division))
                .filter(round -> !round.isFull())
                .findFirst()
                .orElseGet(() -> {
                    String worldName = this.createWorldName(roundFormat, division);
                    CourseImpl course = this.createCourse(roundFormat, division, worldName);
                    GameRound newRound = this.roundHandler.createRound(roundFormat, course, worldName, settings);
                    this.addRoundToQueue(newRound);
                    return newRound;
                });
    }

    private GameRound findOrCreateRoundForParty(
            String roundFormat,
            RoundType type,
            Division division,
            DefaultRoundSettings settings,
            int partySize) {
        return this.queuedRounds.stream()
                .filter(round -> this.matchesQueuedRound(round, roundFormat, type, division))
                .filter(round -> this.hasCapacityForParty(round, roundFormat, partySize))
                .findFirst()
                .orElseGet(() -> {
                    String worldName = this.createWorldName(roundFormat, division);
                    CourseImpl course = this.createCourse(roundFormat, division, worldName);
                    GameRound newRound = this.roundHandler.createRound(roundFormat, course, worldName, settings);
                    this.addRoundToQueue(newRound);
                    return newRound;
                });
    }

    private boolean addPlayerToQueuedRound(GameRound round, UUID playerId, String roundFormat) {
        if (round instanceof FFARound) {
            return round.addTeam(new TeamImpl(playerId, 1));
        }

        if (round instanceof DoublesRound) {
            for (Team team : round.getTeams()) {
                if (!team.isFull()) {
                    team.addPlayer(playerId);
                    return true;
                }
            }

            if (round.isFull()) {
                return false;
            }

            return round.addTeam(new TeamImpl(playerId, 2));
        }

        return false;
    }

    private boolean addPartyToQueuedRound(GameRound round, List<UUID> playerIds, String roundFormat) {
        if (round instanceof FFARound) {
            if (!this.hasCapacityForParty(round, roundFormat, playerIds.size())) {
                return false;
            }

            for (UUID playerId : playerIds) {
                if (!round.addTeam(new TeamImpl(playerId, 1))) {
                    return false;
                }
            }
            return true;
        }

        if (round instanceof DoublesRound) {
            if (!this.hasCapacityForParty(round, roundFormat, playerIds.size())) {
                return false;
            }

            if (playerIds.size() == 1) {
                return this.addPlayerToQueuedRound(round, playerIds.get(0), roundFormat);
            }

            TeamImpl team = new TeamImpl(playerIds.get(0), 2);
            team.addPlayer(playerIds.get(1));
            return round.addTeam(team);
        }

        return false;
    }

    private boolean hasCapacityForParty(GameRound round, String roundFormat, int partySize) {
        if (roundFormat.equalsIgnoreCase("FFA")) {
            return round.getTeams().size() + partySize <= round.getSettings().maxTeams();
        }

        if (roundFormat.equalsIgnoreCase("DOUBLES")) {
            if (partySize == 1) {
                return round.getTeams().stream().anyMatch(team -> !team.isFull())
                        || round.getTeams().size() < round.getSettings().maxTeams();
            }

            return partySize == 2 && round.getTeams().size() < round.getSettings().maxTeams();
        }

        return false;
    }

    private DefaultRoundSettings createSettings(String roundFormat, Division division) {
        if (roundFormat.equalsIgnoreCase("DOUBLES")) {
            return (DefaultRoundSettings) DelegateRoundSettings.create(
                    Constants.DOUBLES_ROUND_MAX_TEAMS,
                    RoundType.RECREATIONAL,
                    division,
                    false);
        }

        return (DefaultRoundSettings) DelegateRoundSettings.create(
                Constants.FFA_ROUND_MAX_PLAYERS,
                RoundType.RECREATIONAL,
                division,
                false);
    }

    private boolean matchesQueuedRound(GameRound round, String roundFormat, RoundType type, Division division) {
        if (round.type() != type || round.division() != division || round.isPrivate()) {
            return false;
        }

        if (roundFormat.equalsIgnoreCase("FFA")) {
            return round instanceof FFARound;
        }

        if (roundFormat.equalsIgnoreCase("DOUBLES")) {
            return round instanceof DoublesRound;
        }

        return false;
    }

    private CourseImpl createCourse(String roundFormat, Division division, String worldName) {
        int size = roundFormat.equalsIgnoreCase("DOUBLES")
                ? Constants.DEFAULT_DUBS_COURSE_SIZE
                : Constants.DEFAULT_FFA_COURSE_SIZE;
        CourseGrid grid = new CourseGrid(size, size, List.of(TileTypes.values()), Constants.DEFAULT_COURSE_HOLES);
        return new CourseCreator().create(division, grid, worldName);
    }

    private String createWorldName(String roundFormat, Division division) {
        return "ndg_"
                + roundFormat.toLowerCase(Locale.ROOT)
                + "_"
                + division.name().toLowerCase(Locale.ROOT)
                + "_"
                + System.currentTimeMillis();
    }

    private boolean isPlayerInActiveRound(UUID playerId) {
        return this.roundHandler.getActiveRounds()
                .stream()
                .anyMatch(round -> round.containsPlayer(playerId))
                || this.roundHandler.getPreparingRounds()
                .stream()
                .anyMatch(round -> round.containsPlayer(playerId));
    }

    private static String normalizeRoundFormat(String roundFormat) {
        if (roundFormat == null) {
            return null;
        }

        if (roundFormat.equalsIgnoreCase("FFA")) {
            return "FFA";
        }

        if (roundFormat.equalsIgnoreCase("DOUBLES")) {
            return "DOUBLES";
        }

        return null;
    }

    private static String displayRoundFormat(String roundFormat) {
        return roundFormat.equalsIgnoreCase("DOUBLES") ? "Doubles" : "FFA";
    }

    private String formatForRound(GameRound round) {
        return round instanceof DoublesRound ? "DOUBLES" : "FFA";
    }
}
