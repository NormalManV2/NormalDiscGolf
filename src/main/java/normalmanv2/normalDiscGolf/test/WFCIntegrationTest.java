package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.settings.RoundType;
import normalmanv2.normalDiscGolf.common.command.AbstractCommand;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.creator.CourseCreator;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.tile.Tile;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.team.TeamImpl;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WFCIntegrationTest extends AbstractCommand {

    private static final long TEST_SEED = 123456789L;

    private final NormalDiscGolfPlugin plugin;

    public WFCIntegrationTest(
            String command,
            String[] aliases,
            String description,
            String permission,
            NormalDiscGolfPlugin plugin
    ) {
        super(command, aliases, description, permission);
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can run this integration test.");
            return;
        }

        UUID playerId = player.getUniqueId();
        PlayerData playerData = NDGManager.getInstance()
                .getPlayerDataManager()
                .getDataByPlayer(playerId);

        if (playerData == null || playerData.getRating() == null) {
            player.sendMessage(ChatColor.RED + "Could not find your disc golf player data.");
            return;
        }

        Division division = playerData.getRating().getDivision();

        int size = readIntArg(args, 0, Constants.DEFAULT_FFA_COURSE_SIZE);
        int holes = readIntArg(args, 1, Constants.DEFAULT_COURSE_HOLES);
        boolean generateObstacles = readBooleanArg(args, 2, true);

        String worldName = "wfc_test_" + player.getName().toLowerCase() + "_" + System.currentTimeMillis();

        player.sendMessage(ChatColor.YELLOW + "Creating WFC integration test round...");
        player.sendMessage(ChatColor.GRAY + "World: " + worldName);
        player.sendMessage(ChatColor.GRAY + "Size: " + size + "x" + size);
        player.sendMessage(ChatColor.GRAY + "Holes: " + holes);
        player.sendMessage(ChatColor.GRAY + "Division: " + division);
        player.sendMessage(ChatColor.GRAY + "Seed: " + TEST_SEED);
        player.sendMessage(ChatColor.GRAY + "Generate obstacles: " + generateObstacles);

        CourseGrid grid = new CourseGrid(
                size,
                size,
                List.of(TileTypes.values()),
                holes,
                TEST_SEED
        );

        CourseImpl course = new CourseCreator().create(
                division,
                grid,
                worldName
        );

        try {
            player.sendMessage(ChatColor.YELLOW + "Generating and validating course layout...");
            grid.generate(division);
        } catch (Exception exception) {
            player.sendMessage(ChatColor.RED + "Course layout generation failed: " + exception.getMessage());
            exception.printStackTrace();
            return;
        }

        CourseValidationResult validationResult = validateCourseGrid(grid, holes);

        if (!validationResult.success()) {
            player.sendMessage(ChatColor.RED + "Course grid validation failed:");
            for (String error : validationResult.errors()) {
                player.sendMessage(ChatColor.RED + "- " + error);
            }
            return;
        }

        sendValidationSummary(player, grid, validationResult);

        DefaultRoundSettings settings = new DefaultRoundSettings(
                1,
                RoundType.RECREATIONAL,
                division,
                false
        );

        try {
            GameRound round = NDGManager.getInstance()
                    .getRoundHandler()
                    .createRound("ffa", course, worldName, settings, generateObstacles);

            if (!round.addTeam(new TeamImpl(playerId, 1))) {
                player.sendMessage(ChatColor.RED + "Could not add you to the generated solo round.");
                return;
            }

            NDGManager.getInstance().getRoundHandler().startRound(round);
        } catch (Exception exception) {
            player.sendMessage(ChatColor.RED + "Failed to start generated solo round: " + exception.getMessage());
            exception.printStackTrace();
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Started a playable solo FFA round on the generated course.");
        player.sendMessage(ChatColor.GRAY + "You will be teleported to hole 1's tee shortly.");
        player.sendMessage(ChatColor.GRAY + "Use /throwdisc putter backhand when you are ready.");

        if (generateObstacles) {
            player.sendMessage(ChatColor.GRAY + "Obstacles are being generated gradually after round start.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of(
                    String.valueOf(Constants.DEFAULT_FFA_COURSE_SIZE),
                    "32",
                    "64",
                    "128"
            );
        }

        if (args.length == 2) {
            return List.of(
                    String.valueOf(Constants.DEFAULT_COURSE_HOLES),
                    "3",
                    "9",
                    "18"
            );
        }

        if (args.length == 3) {
            return List.of("true", "false");
        }

        return List.of();
    }

    private static int readIntArg(String[] args, int index, int defaultValue) {
        if (args.length <= index) return defaultValue;

        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static boolean readBooleanArg(String[] args, int index, boolean defaultValue) {
        if (args.length <= index) return defaultValue;

        return switch (args[index].toLowerCase()) {
            case "true", "yes", "y", "1" -> true;
            case "false", "no", "n", "0" -> false;
            default -> defaultValue;
        };
    }

    private static CourseValidationResult validateCourseGrid(CourseGrid grid, int expectedHoles) {
        List<String> errors = new java.util.ArrayList<>();
        Map<TileTypes, Integer> tileCounts = new EnumMap<>(TileTypes.class);
        Map<Integer, Integer> routeLengths = new java.util.TreeMap<>();
        Map<Integer, List<CourseGrid.GridPoint>> routePoints = grid.getHoleRoutePoints();

        for (TileTypes type : TileTypes.values()) {
            tileCounts.put(type, 0);
        }

        if (grid.getTeePoints().size() != expectedHoles) {
            errors.add("Expected " + expectedHoles + " tee points, found " + grid.getTeePoints().size());
        }

        if (grid.getHolePoints().size() != expectedHoles) {
            errors.add("Expected " + expectedHoles + " hole points, found " + grid.getHolePoints().size());
        }

        if (grid.getHolePars().size() != expectedHoles) {
            errors.add("Expected " + expectedHoles + " hole pars, found " + grid.getHolePars().size());
        }

        if (routePoints.size() != expectedHoles) {
            errors.add("Expected " + expectedHoles + " mapped routes, found " + routePoints.size());
        }

        for (int hole = 0; hole < expectedHoles; hole++) {
            CourseGrid.GridPoint teePoint = grid.getTeePoints().get(hole);
            CourseGrid.GridPoint pinPoint = grid.getHolePoints().get(hole);

            if (teePoint == null) {
                errors.add("Missing tee point for hole " + (hole + 1));
            }

            if (pinPoint == null) {
                errors.add("Missing pin point for hole " + (hole + 1));
            }

            Integer par = grid.getHolePars().get(hole);
            if (par == null) {
                errors.add("Missing par for hole " + (hole + 1));
            } else if (par < 3 || par > 6) {
                errors.add("Invalid par for hole " + (hole + 1) + ": " + par);
            }

            List<CourseGrid.GridPoint> route = routePoints.get(hole);
            if (route == null || route.size() < 2) {
                errors.add("Missing route from tee to pin for hole " + (hole + 1));
                continue;
            }

            routeLengths.put(hole + 1, route.size());

            if (teePoint != null && !sameTile(route.get(0), teePoint)) {
                errors.add("Hole " + (hole + 1) + " route does not start on its tee tile");
            }

            if (pinPoint != null && !sameTile(route.get(route.size() - 1), pinPoint)) {
                errors.add("Hole " + (hole + 1) + " route does not end on its pin tile");
            }

            String routeError = validateRouteCenterline(grid, route);
            if (routeError != null) {
                errors.add("Hole " + (hole + 1) + " " + routeError);
            }
        }

        int nullTiles = 0;
        int uncollapsedTiles = 0;
        int collapsedWithoutState = 0;

        for (int x = 0; x < grid.getWidth(); x++) {
            for (int z = 0; z < grid.getDepth(); z++) {
                Tile tile = grid.getTile(x, z);

                if (tile == null) {
                    nullTiles++;
                    continue;
                }

                if (!tile.isCollapsed()) {
                    uncollapsedTiles++;
                    continue;
                }

                TileTypes state = tile.getCollapsedState();
                if (state == null) {
                    collapsedWithoutState++;
                    continue;
                }

                tileCounts.merge(state, 1, Integer::sum);
            }
        }

        if (nullTiles > 0) {
            errors.add("Found " + nullTiles + " null tiles inside grid bounds");
        }

        if (uncollapsedTiles > 0) {
            errors.add("Found " + uncollapsedTiles + " uncollapsed tiles");
        }

        if (collapsedWithoutState > 0) {
            errors.add("Found " + collapsedWithoutState + " collapsed tiles with null state");
        }

        int teeTiles = tileCounts.getOrDefault(TileTypes.TEE, 0);
        int pinTiles = tileCounts.getOrDefault(TileTypes.PIN, 0);
        int fairwayTiles = tileCounts.getOrDefault(TileTypes.FAIRWAY, 0);

        if (teeTiles != expectedHoles) {
            errors.add("Expected " + expectedHoles + " actual TEE tiles, found " + teeTiles);
        }

        if (pinTiles != expectedHoles) {
            errors.add("Expected " + expectedHoles + " actual PIN tiles, found " + pinTiles);
        }

        if (fairwayTiles <= 0) {
            errors.add("No FAIRWAY tiles were generated");
        }

        return new CourseValidationResult(errors.isEmpty(), errors, tileCounts, routeLengths);
    }

    private static String validateRouteCenterline(CourseGrid grid, List<CourseGrid.GridPoint> route) {
        for (int i = 0; i < route.size(); i++) {
            CourseGrid.GridPoint point = route.get(i);
            int tileX = tileX(point);
            int tileZ = tileZ(point);
            Tile tile = grid.getTile(tileX, tileZ);

            if (tile == null) {
                return "route leaves the generated grid at " + formatTile(tileX, tileZ);
            }

            if (i > 0) {
                CourseGrid.GridPoint previous = route.get(i - 1);
                int previousX = tileX(previous);
                int previousZ = tileZ(previous);
                int manhattanDistance = Math.abs(tileX - previousX) + Math.abs(tileZ - previousZ);
                if (manhattanDistance != 1) {
                    return "route has a gap between " + formatTile(previousX, previousZ) + " and " + formatTile(tileX, tileZ);
                }
            }

            TileTypes state = tile.getCollapsedState();
            boolean endpoint = i == 0 || i == route.size() - 1;
            if (!endpoint && state != TileTypes.FAIRWAY) {
                return "route centerline is broken at " + formatTile(tileX, tileZ) + " by " + state;
            }
        }

        return null;
    }

    private static boolean sameTile(CourseGrid.GridPoint a, CourseGrid.GridPoint b) {
        return tileX(a) == tileX(b) && tileZ(a) == tileZ(b);
    }

    private static int tileX(CourseGrid.GridPoint point) {
        return Math.floorDiv(point.x(), Constants.DEFAULT_TILE_SIZE);
    }

    private static int tileZ(CourseGrid.GridPoint point) {
        return Math.floorDiv(point.z(), Constants.DEFAULT_TILE_SIZE);
    }

    private static String formatTile(int x, int z) {
        return "(" + x + "," + z + ")";
    }

    private static void sendValidationSummary(Player player, CourseGrid grid, CourseValidationResult result) {
        player.sendMessage(ChatColor.GREEN + "Course grid validation passed.");
        player.sendMessage(ChatColor.GRAY + "Grid: " + grid.getWidth() + "x" + grid.getDepth());
        player.sendMessage(ChatColor.GRAY + "Holes: " + grid.getNumHoles());
        player.sendMessage(ChatColor.GRAY + "Tees: " + grid.getTeePoints().size());
        player.sendMessage(ChatColor.GRAY + "Pins: " + grid.getHolePoints().size());
        player.sendMessage(ChatColor.GRAY + "Pars: " + grid.getHolePars());
        player.sendMessage(ChatColor.GRAY + "Routes: " + result.routeLengths());

        player.sendMessage(ChatColor.YELLOW + "Tile counts:");
        for (Map.Entry<TileTypes, Integer> entry : result.tileCounts().entrySet()) {
            player.sendMessage(ChatColor.GRAY + "- " + entry.getKey() + ": " + entry.getValue());
        }
    }

    private record CourseValidationResult(
            boolean success,
            List<String> errors,
            Map<TileTypes, Integer> tileCounts,
            Map<Integer, Integer> routeLengths
    ) {
    }
}
