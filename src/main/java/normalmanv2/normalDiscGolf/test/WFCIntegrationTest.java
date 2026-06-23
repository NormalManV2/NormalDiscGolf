package normalmanv2.normalDiscGolf.test;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.common.command.AbstractCommand;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.creator.CourseCreator;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGridWorldCreator;
import normalmanv2.normalDiscGolf.impl.course.tile.Tile;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
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

        Division division = NDGManager.getInstance()
                .getPlayerDataManager()
                .getDataByPlayer(playerId)
                .getRating()
                .getDivision();

        int size = readIntArg(args, 0, Constants.DEFAULT_FFA_COURSE_SIZE);
        int holes = readIntArg(args, 1, Constants.DEFAULT_COURSE_HOLES);
        boolean generateObstacles = readBooleanArg(args, 2, true);

        String worldName = "wfc_test_" + player.getName().toLowerCase() + "_" + System.currentTimeMillis();

        player.sendMessage(ChatColor.YELLOW + "Creating WFC integration test world...");
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

        WorldCreator worldCreator = new CourseGridWorldCreator(worldName, course, division);
        World world = Bukkit.createWorld(worldCreator);

        if (world == null) {
            player.sendMessage(ChatColor.RED + "Failed to create test world.");
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

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (generateObstacles) {
                try {
                    player.sendMessage(ChatColor.YELLOW + "Generating obstacles...");
                    grid.generateObstacles(world);
                    player.sendMessage(ChatColor.GREEN + "Obstacle generation completed.");
                } catch (Exception exception) {
                    player.sendMessage(ChatColor.RED + "Obstacle generation failed: " + exception.getMessage());
                    exception.printStackTrace();
                    return;
                }
            }

            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                CourseGrid.GridPoint startingPoint = grid.getTeePoints().get(0);

                if (startingPoint == null) {
                    player.sendMessage(ChatColor.RED + "Could not teleport: missing tee for hole 0.");
                    return;
                }

                player.teleport(grid.toLocation(world, startingPoint));
                player.sendMessage(ChatColor.GREEN + "Teleported to generated course starting tee.");
            }, 20L);
        }, 40L);
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

        for (int hole = 0; hole < expectedHoles; hole++) {
            if (!grid.getTeePoints().containsKey(hole)) {
                errors.add("Missing tee point for hole " + hole);
            }

            if (!grid.getHolePoints().containsKey(hole)) {
                errors.add("Missing pin point for hole " + hole);
            }

            Integer par = grid.getHolePars().get(hole);
            if (par == null) {
                errors.add("Missing par for hole " + hole);
            } else if (par < 3 || par > 6) {
                errors.add("Invalid par for hole " + hole + ": " + par);
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

        if (teeTiles <= 0) {
            errors.add("No TEE tiles were generated");
        }

        if (pinTiles <= 0) {
            errors.add("No PIN tiles were generated");
        }

        if (fairwayTiles <= 0) {
            errors.add("No FAIRWAY tiles were generated");
        }

        return new CourseValidationResult(errors.isEmpty(), errors, tileCounts);
    }

    private static void sendValidationSummary(Player player, CourseGrid grid, CourseValidationResult result) {
        player.sendMessage(ChatColor.GREEN + "Course grid validation passed.");
        player.sendMessage(ChatColor.GRAY + "Grid: " + grid.getWidth() + "x" + grid.getDepth());
        player.sendMessage(ChatColor.GRAY + "Holes: " + grid.getNumHoles());
        player.sendMessage(ChatColor.GRAY + "Tees: " + grid.getTeePoints().size());
        player.sendMessage(ChatColor.GRAY + "Pins: " + grid.getHolePoints().size());
        player.sendMessage(ChatColor.GRAY + "Pars: " + grid.getHolePars());

        player.sendMessage(ChatColor.YELLOW + "Tile counts:");
        for (Map.Entry<TileTypes, Integer> entry : result.tileCounts().entrySet()) {
            player.sendMessage(ChatColor.GRAY + "- " + entry.getKey() + ": " + entry.getValue());
        }
    }

    private record CourseValidationResult(
            boolean success,
            List<String> errors,
            Map<TileTypes, Integer> tileCounts
    ) {
    }
}
