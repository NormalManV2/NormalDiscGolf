package normalmanv2.normalDiscGolf.impl.manager.round.queue;

import normalmanv2.normalDiscGolf.impl.util.Bounds;
import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.delegate.settings.DelegateRoundSettings;
import normalmanv2.normalDiscGolf.api.round.settings.RoundType;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.round.*;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.creator.CourseCreator;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGenerationSettings;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGenerationProfile;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseTerrain;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.custom.CustomCourse;
import normalmanv2.normalDiscGolf.impl.course.library.SavedCourse;
import normalmanv2.normalDiscGolf.impl.competition.DailyCourse;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.manager.round.lifecycle.RoundHandler;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.round.DoublesRound;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.round.MatchPlayRound;
import normalmanv2.normalDiscGolf.impl.round.EliminationRound;
import normalmanv2.normalDiscGolf.impl.round.RoundFormat;
import normalmanv2.normalDiscGolf.impl.round.DailyRound;
import normalmanv2.normalDiscGolf.impl.team.TeamImpl;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

public class RoundQueueManager {

    public enum QueueResult {
        SUCCESS,
        INVALID_FORMAT,
        RESOURCE_PACK_REQUIRED,
        PLAYER_DATA_MISSING,
        PLAYER_OFFLINE,
        PLAYER_IN_ACTIVE_ROUND,
        PLAYER_ALREADY_QUEUED,
        DIVISION_MISMATCH,
        PARTY_TOO_LARGE,
        ROUND_FULL,
        CUSTOM_COURSE_BUSY,
        DAILY_UNAVAILABLE,
        LOBBY_UNAVAILABLE,
        TERRAIN_UNAVAILABLE,
        PRESET_UNAVAILABLE
    }

    public record Lobby(String id, CourseTerrain terrain, long seed, int holes, String preset, int capacity,
                        List<String> players, String status) {}

    private final RoundHandler roundHandler;
    private final Set<GameRound> queuedRounds;
    private final Map<Division, Map<UUID, String>> playerQueue;
    private final PlayerDataManager playerDataManager;
    private final Map<GameRound, Long> readySince = new HashMap<>();

    public RoundQueueManager(RoundHandler roundHandler, PlayerDataManager playerDataManager) {
        this.roundHandler = roundHandler;
        this.playerDataManager = playerDataManager;
        this.queuedRounds = new LinkedHashSet<>();
        this.playerQueue = new HashMap<>();
        for (Division division : Division.values()) {
            this.playerQueue.put(division, new HashMap<>());
        }
    }

    public void clear() {
        this.queuedRounds.clear();
        this.playerQueue.values().forEach(Map::clear);
        this.readySince.clear();
    }

    public boolean addPlayerToQueue(UUID player, String roundFormat) {
        String normalizedFormat = normalizeRoundFormat(roundFormat);
        if (!packReady(player)) return false;
        if (normalizedFormat == null || this.isPlayerInActiveRound(player)) {
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

        if (RoundFormat.DAILY.name().equals(normalizedFormat) && currentDaily() == null) return false;
        this.removePlayerFromQueue(player);
        Division division = Division.JUNIOR;
        this.playerQueue.get(division).put(player, normalizedFormat);
        return true;
    }

    public QueueResult addPartyToQueue(Collection<UUID> players, String roundFormat) {
        return addPartyToQueue(players, roundFormat, null, null);
    }

    public QueueResult addPartyToQueue(Collection<UUID> players, String roundFormat, String lobby, CourseTerrain terrain) {
        return addPartyToQueue(players, roundFormat, lobby, terrain, null);
    }

    public QueueResult addPartyToQueue(Collection<UUID> players, String roundFormat, String lobby, CourseTerrain terrain,
                                      String preset) {
        String normalizedFormat = normalizeRoundFormat(roundFormat);
        if (normalizedFormat == null) {
            return QueueResult.INVALID_FORMAT;
        }

        List<UUID> playerIds = players.stream().distinct().toList();
        if (playerIds.isEmpty()) {
            return QueueResult.PLAYER_DATA_MISSING;
        }

        RoundFormat format = RoundFormat.parse(normalizedFormat);
        if (format.usesDoublesTeams() && playerIds.size() > Constants.MAXIMUM_PARTY_MEMBERS) {
            return QueueResult.PARTY_TOO_LARGE;
        }

        if (format.usesIndividualTeams()
                && playerIds.size() > Constants.FFA_ROUND_MAX_PLAYERS) {
            return QueueResult.PARTY_TOO_LARGE;
        }
        if (normalizedFormat.equals("MATCH") && playerIds.size() > MatchPlayRound.PLAYERS) {
            return QueueResult.PARTY_TOO_LARGE;
        }

        QueueResult ready = validatePlayers(playerIds);
        if (ready != QueueResult.SUCCESS) return ready;
        if (preset != null && (format == RoundFormat.DAILY || terrain == null || !"new".equalsIgnoreCase(lobby)))
            return QueueResult.PRESET_UNAVAILABLE;
        var config = config();
        var profile = preset == null ? CourseGenerationProfile.serverDefault(config)
                : CourseGenerationProfile.selectable(config, preset);
        if (profile == null) return QueueResult.PRESET_UNAVAILABLE;
        DailyCourse.Definition daily = format == RoundFormat.DAILY ? currentDaily() : null;
        if (format == RoundFormat.DAILY && daily == null) return QueueResult.DAILY_UNAVAILABLE;
        if (terrain != null && (format == RoundFormat.DAILY || !profile.terrains().contains(terrain)))
            return QueueResult.TERRAIN_UNAVAILABLE;
        Division division = Division.JUNIOR; // Recreational matchmaking keeps friends together regardless of rating.
        DefaultRoundSettings settings = this.createSettings(normalizedFormat, division);
        if (lobby != null && !lobby.equalsIgnoreCase("new")) {
            return queuedRounds.stream()
                    .filter(round -> round.getId().equals(lobby))
                    .filter(round -> matchesQueuedRound(round, normalizedFormat, RoundType.RECREATIONAL, division, daily))
                    .findFirst().map(round -> commitParty(round, playerIds, normalizedFormat))
                    .orElse(QueueResult.LOBBY_UNAVAILABLE);
        }

        if (lobby == null) {
            var existing = queuedRounds.stream()
                    .filter(round -> matchesQueuedRound(round, normalizedFormat, RoundType.RECREATIONAL, division, daily))
                    .filter(round -> hasCapacityForParty(round, normalizedFormat, playerIds.size())).findFirst();
            if (existing.isPresent()) return commitParty(existing.get(), playerIds, normalizedFormat);
        }
        GameRound round = createLobby(normalizedFormat, division, settings, daily, terrain, profile);
        QueueResult joined = commitParty(round, playerIds, normalizedFormat);
        if (joined == QueueResult.SUCCESS) addRoundToQueue(round);
        else roundHandler.discardRound(round);
        return joined;
    }

    public List<CourseTerrain> availableTerrains() {
        var config = config();
        var terrains = new LinkedHashSet<>(CourseGenerationProfile.serverDefault(config).terrains());
        CourseGenerationProfile.selectable(config).forEach(profile -> terrains.addAll(profile.terrains()));
        return List.copyOf(terrains);
    }

    public List<CourseGenerationProfile> lobbyProfiles(CourseTerrain terrain) {
        var config = config();
        var profiles = new ArrayList<CourseGenerationProfile>();
        var serverDefault = CourseGenerationProfile.serverDefault(config);
        if (serverDefault.terrains().contains(terrain)) profiles.add(serverDefault);
        CourseGenerationProfile.selectable(config).stream().filter(profile -> profile.terrains().contains(terrain))
                .forEach(profiles::add);
        return List.copyOf(profiles);
    }

    public int lobbyHoles(RoundFormat format, CourseGenerationProfile profile) {
        return format == RoundFormat.ACE_RACE || format == RoundFormat.CLOSEST_TO_PIN
                ? Bounds.clamp(config().getInt(format.id() + ".holes", 6), 1, 18) : profile.holes();
    }

    private ConfigurationSection config() {
        return NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getConfig();
    }

    public List<Lobby> lobbies(RoundFormat format) {
        return queuedRounds.stream()
                .filter(round -> !round.isPrivate() && !(round.getCourse() instanceof CustomCourse)
                        && RoundFormat.of(round) == format)
                .filter(round -> !round.getTeams().isEmpty())
                .map(round -> {
                    var course = (CourseImpl) round.getCourse();
                    List<String> names = round.getTeams().stream().flatMap(team -> team.getTeamMembers().stream())
                            .map(Bukkit::getPlayer).filter(Objects::nonNull).map(Player::getName).toList();
                    int capacity = round.getSettings().maxTeams() * (format.usesDoublesTeams() ? 2 : 1);
                    Long since = readySince.get(round);
                    String status = roundHandler.isBuildingCourse(round) ? "Preparing course"
                            : !roundHandler.courseReady(round) ? "Waiting to prepare course"
                            : since == null ? "Course ready · Waiting for players" : "Starting in " + secondsRemaining(since) + "s";
                    return new Lobby(round.getId(), course.grid().terrain(), course.seed(), course.holes(),
                            course.generationPreset(), capacity, names, status);
                }).toList();
    }

    public QueueResult addPlayersToCustomCourse(Collection<UUID> players, CustomCourse course) {
        List<UUID> playerIds = players.stream().distinct().toList();
        if (playerIds.isEmpty()) return QueueResult.PLAYER_DATA_MISSING;
        if (playerIds.size() > Constants.FFA_ROUND_MAX_PLAYERS) return QueueResult.PARTY_TOO_LARGE;
        QueueResult ready = validatePlayers(playerIds);
        if (ready != QueueResult.SUCCESS) return ready;
        if (roundHandler.getActiveRounds().stream().anyMatch(round -> usesCustomCourse(round, course)))
            return QueueResult.CUSTOM_COURSE_BUSY;
        for (GameRound queued : queuedRounds) {
            if (!usesCustomCourse(queued, course)) continue;
            if (queued.getCourse() != course) return QueueResult.CUSTOM_COURSE_BUSY;
            return commitParty(queued, playerIds, RoundFormat.FFA.id());
        }
        var settings = new DefaultRoundSettings(Constants.FFA_ROUND_MAX_PLAYERS, RoundType.RECREATIONAL, Division.JUNIOR, true);
        GameRound round = roundHandler.createCustomRound(course, settings);
        QueueResult joined = commitParty(round, playerIds, RoundFormat.FFA.id());
        if (joined == QueueResult.SUCCESS) addRoundToQueue(round);
        else roundHandler.discardRound(round);
        return joined;
    }

    private static boolean usesCustomCourse(GameRound round, CustomCourse course) {
        return round.getCourse() instanceof CustomCourse other && other.id().equals(course.id());
    }

    public QueueResult addPlayersToSavedCourse(Collection<UUID> players, SavedCourse saved, boolean forRecord) {
        List<UUID> playerIds = players.stream().distinct().toList();
        if (playerIds.isEmpty()) return QueueResult.PLAYER_DATA_MISSING;
        if (playerIds.size() > Constants.FFA_ROUND_MAX_PLAYERS) return QueueResult.PARTY_TOO_LARGE;
        QueueResult ready = validatePlayers(playerIds);
        if (ready != QueueResult.SUCCESS) return ready;

        String format = (forRecord ? RoundFormat.FFA : saved.replayFormat()).id();
        String worldName = createWorldName(format, Division.JUNIOR);
        var settings = new DefaultRoundSettings(Constants.FFA_ROUND_MAX_PLAYERS, RoundType.RECREATIONAL, Division.JUNIOR, true);
        GameRound round = roundHandler.createQueuedRound(format, saved.instantiate(settings.division(), worldName), worldName, settings);
        QueueResult joined = commitParty(round, playerIds, format);
        if (joined == QueueResult.SUCCESS) addRoundToQueue(round);
        else roundHandler.discardRound(round);
        return joined;
    }

    private QueueResult validatePlayers(List<UUID> playerIds) {
        return validatePlayers(playerIds, true);
    }

    public QueueResult addSetupTest(UUID owner, CourseGenerationSettings generation, long seed) {
        List<UUID> players = List.of(owner);
        QueueResult ready = validatePlayers(players, false);
        if (ready != QueueResult.SUCCESS) return ready;
        Division division = Division.JUNIOR;
        String world = createWorldName("setup", division);
        var grid = new CourseGrid(23, 9, List.of(TileTypes.values()), 1, seed, CourseGrid.Layout.PARKLAND, generation);
        var settings = new DefaultRoundSettings(1, RoundType.RECREATIONAL, division, true);
        var round = roundHandler.createQueuedSetupTest(new CourseImpl(division, grid, world), world, settings);
        QueueResult joined = commitParty(round, players, RoundFormat.FFA.id());
        if (joined == QueueResult.SUCCESS) addRoundToQueue(round);
        else roundHandler.discardRound(round);
        return joined;
    }

    private QueueResult validatePlayers(List<UUID> playerIds, boolean requirePack) {
        for (UUID playerId : playerIds) {
            if (Bukkit.getPlayer(playerId) == null) {
                return QueueResult.PLAYER_OFFLINE;
            }
            if (requirePack && !packReady(playerId)) return QueueResult.RESOURCE_PACK_REQUIRED;

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

        }
        return QueueResult.SUCCESS;
    }

    public boolean readyForScheduledRound(UUID id) {
        return validatePlayers(List.of(id)) == QueueResult.SUCCESS;
    }

    private QueueResult commitParty(GameRound round, List<UUID> playerIds, String format) {
        if (round == null || !this.addPartyToQueuedRound(round, playerIds, format)) return QueueResult.ROUND_FULL;
        // Membership is already in queuedRounds. Only remove the staging entries here.
        for (UUID id : playerIds) this.playerQueue.values().forEach(queue -> queue.remove(id));
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

    public Optional<CustomCourse> getQueuedCustomCourse(UUID id) {
        return queuedRounds.stream().filter(round -> round.containsPlayer(id))
                .map(GameRound::getCourse).filter(CustomCourse.class::isInstance).map(CustomCourse.class::cast).findFirst();
    }

    public void addRoundToQueue(GameRound round) {
        this.queuedRounds.add(round);
    }

    public void removeRoundFromQueue(GameRound round) {
        this.queuedRounds.remove(round);
        this.readySince.remove(round);
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
                DailyCourse.Definition daily = RoundFormat.DAILY.name().equals(roundFormat) ? currentDaily() : null;
                if (RoundFormat.DAILY.name().equals(roundFormat) && daily == null) {
                    iterator.remove();
                    player.sendMessage(ChatColor.RED + "The Daily Course is currently unavailable.");
                    continue;
                }
                DefaultRoundSettings settings = this.createSettings(roundFormat, division);
                GameRound round = findOrCreateRoundForDivision(
                        roundFormat,
                        RoundType.RECREATIONAL,
                        division,
                        settings, daily);

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

            round.getRoundTeamManager().tick();
            if (round.getTeams().isEmpty()) {
                iterator.remove();
                readySince.remove(round);
                this.roundHandler.discardRound(round);
                continue;
            }
            if (!this.shouldStartRound(round)) {
                readySince.remove(round);
                continue;
            }
            long since = readySince.computeIfAbsent(round, ignored -> System.currentTimeMillis());
            long remaining = secondsRemaining(since);
            boolean courseReady = this.roundHandler.courseReady(round);
            String message = courseReady ? "§aRound starts in " + remaining + "s" : "§eWaiting for the course to be ready";
            for (Team team : round.getTeams()) for (UUID id : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(message + " §7• " + round.getTeams().size() + " teams"));
            }
            if (!courseReady || remaining > 0 && !round.isFull()) continue;
            readySince.remove(round);

            iterator.remove();
            this.roundHandler.startRound(round);
            return;
        }
    }

    private static long secondsRemaining(long since) {
        int seconds = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getConfig().getInt("queue-countdown-seconds", 15);
        return Math.max(0, seconds - (System.currentTimeMillis() - since) / 1000);
    }

    private boolean shouldStartRound(GameRound round) {
        if (round instanceof EliminationRound elimination) return elimination.hasOpponents();
        if (round instanceof MatchPlayRound) {
            return round.getTeams().size() == MatchPlayRound.PLAYERS && round.getTeams().stream().allMatch(Team::isFull);
        }
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
                readySince.remove(round);
                this.roundHandler.discardRound(round);
            }
        }

        return removed;
    }

    private GameRound findOrCreateRoundForDivision(
            String roundFormat,
            RoundType type,
            Division division,
            DefaultRoundSettings settings, DailyCourse.Definition daily) {
        return this.queuedRounds.stream()
                .filter(round -> this.matchesQueuedRound(round, roundFormat, type, division, daily))
                .filter(round -> this.hasCapacityForParty(round, roundFormat, 1))
                .findFirst()
                .orElseGet(() -> {
                    GameRound newRound = createLobby(roundFormat, division, settings, daily, null,
                            CourseGenerationProfile.serverDefault(config()));
                    this.addRoundToQueue(newRound);
                    return newRound;
                });
    }

    private GameRound createLobby(String roundFormat, Division division, DefaultRoundSettings settings,
                                  DailyCourse.Definition daily, CourseTerrain terrain, CourseGenerationProfile profile) {
        String worldName = this.createWorldName(roundFormat, division);
        CourseImpl course = this.createCourse(roundFormat, division, worldName, daily, terrain, profile);
        return this.roundHandler.createQueuedRound(roundFormat, course, worldName, settings);
    }

    private boolean addPlayerToQueuedRound(GameRound round, UUID playerId, String roundFormat) {
        if (round instanceof FFARound || round instanceof MatchPlayRound) {
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
        if (round instanceof FFARound || round instanceof MatchPlayRound) {
            if (!this.hasCapacityForParty(round, roundFormat, playerIds.size())) {
                return false;
            }

            List<Team> added = new ArrayList<>();
            for (UUID playerId : playerIds) {
                Team team = new TeamImpl(playerId, 1);
                if (!round.addTeam(team)) {
                    added.forEach(round::removeTeam);
                    return false;
                }
                added.add(team);
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

            List<Team> added = new ArrayList<>();
            for (int i = 0; i < playerIds.size(); i += 2) {
                TeamImpl team = new TeamImpl(playerIds.get(i), 2);
                if (i + 1 < playerIds.size()) team.addPlayer(playerIds.get(i + 1));
                if (!round.addTeam(team)) {
                    added.forEach(round::removeTeam);
                    return false;
                }
                added.add(team);
            }
            return true;
        }

        return false;
    }

    private boolean hasCapacityForParty(GameRound round, String roundFormat, int partySize) {
        if (round instanceof FFARound || round instanceof MatchPlayRound) {
            return round.getTeams().size() + partySize <= round.getSettings().maxTeams();
        }

        if (RoundFormat.parse(roundFormat).usesDoublesTeams()) {
            if (partySize == 1) {
                return round.getTeams().stream().anyMatch(team -> !team.isFull())
                        || round.getTeams().size() < round.getSettings().maxTeams();
            }

            return partySize <= Constants.MAXIMUM_PARTY_MEMBERS
                    && round.getTeams().size() + (partySize + 1) / 2 <= round.getSettings().maxTeams();
        }

        return false;
    }

    private DefaultRoundSettings createSettings(String roundFormat, Division division) {
        if (RoundFormat.parse(roundFormat).usesDoublesTeams()) {
            return (DefaultRoundSettings) DelegateRoundSettings.create(
                    Constants.DOUBLES_ROUND_MAX_TEAMS,
                    RoundType.RECREATIONAL,
                    division,
                    false);
        }

        return (DefaultRoundSettings) DelegateRoundSettings.create(
                roundFormat.equalsIgnoreCase("MATCH") ? MatchPlayRound.PLAYERS : Constants.FFA_ROUND_MAX_PLAYERS,
                RoundType.RECREATIONAL,
                division,
                false);
    }

    private boolean matchesQueuedRound(GameRound round, String roundFormat, RoundType type, Division division,
                                       DailyCourse.Definition daily) {
        if (round.type() != type || round.division() != division || round.isPrivate() || round.getCourse() instanceof CustomCourse) {
            return false;
        }

        RoundFormat requested = RoundFormat.parse(roundFormat);
        return requested != null && requested == RoundFormat.of(round)
                && (daily == null || round instanceof DailyRound existing && daily.equals(existing.definition()));
    }

    private CourseImpl createCourse(String roundFormat, Division division, String worldName, DailyCourse.Definition daily,
                                    CourseTerrain terrain, CourseGenerationProfile profile) {
        if (daily != null) return new DailyCourse(daily, division, worldName);
        RoundFormat format = RoundFormat.parse(roundFormat);
        boolean shortLayout = format == RoundFormat.ACE_RACE || format == RoundFormat.CLOSEST_TO_PIN;
        int holes = lobbyHoles(format, profile);
        var layout = shortLayout ? CourseGrid.Layout.SHORT : CourseGrid.Layout.PARKLAND;
        long seed = profile.nextSeed();
        CourseGrid grid = new CourseGrid(23, ((holes + 2) / 3) * 7 + 2, List.of(TileTypes.values()),
                holes, seed, layout, terrain == null ? profile.settings(seed) : profile.settings(terrain));
        return new CourseCreator().create(division, grid, worldName, profile.id() == null ? null : profile.label());
    }

    private DailyCourse.Definition currentDaily() {
        try {
            return NDGManager.getInstance().getDailyCompetition().today().course();
        } catch (java.io.IOException failure) {
            normalmanv2.normalDiscGolf.NormalDiscGolfPlugin.getPlugin(normalmanv2.normalDiscGolf.NormalDiscGolfPlugin.class)
                    .getLogger().log(java.util.logging.Level.WARNING, "Could not load the Daily Course", failure);
            return null;
        }
    }

    private String createWorldName(String roundFormat, Division division) {
        return "ndg_"
                + roundFormat.toLowerCase(Locale.ROOT)
                + "_"
                + division.name().toLowerCase(Locale.ROOT)
                + "_"
                + UUID.randomUUID();
    }

    private boolean isPlayerInActiveRound(UUID playerId) {
        return this.roundHandler.getActiveRounds()
                .stream()
                .anyMatch(round -> round.containsPlayer(playerId))
                || this.roundHandler.getPreparingRounds()
                .stream()
                .filter(round -> !this.isRoundQueued(round))
                .anyMatch(round -> round.containsPlayer(playerId));
    }

    private boolean packReady(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player == null) return false;
        try {
            if (NDGManager.getInstance().getPacked().ready(player)) return true;
        } catch (java.io.IOException | RuntimeException exception) {
            if (!normalmanv2.normalDiscGolf.NormalDiscGolfPlugin.getPlugin(normalmanv2.normalDiscGolf.NormalDiscGolfPlugin.class).getConfig().getBoolean("resource-pack.required", false)) return true;
        }
        player.sendMessage("§eAccept the server resource pack and wait for it to finish loading before queueing.");
        return false;
    }

    private static String normalizeRoundFormat(String roundFormat) {
        RoundFormat format = RoundFormat.parse(roundFormat);
        return format == null || !format.canQueueDirectly() ? null : format.name();
    }

    private static String displayRoundFormat(String roundFormat) {
        return RoundFormat.parse(roundFormat).label();
    }

    private String formatForRound(GameRound round) {
        RoundFormat format = RoundFormat.of(round);
        return format == null ? "FFA" : format.name();
    }
}
