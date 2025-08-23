package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.event.PlayerQueueRoundEvent;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class PlayerRoundQueueListener implements Listener {

    private final RoundQueueManager queueManager = NDGManager.getInstance().getRoundQueueManager();

    public PlayerRoundQueueListener() {
    }

    @EventHandler
    public void onRoundQueue(PlayerQueueRoundEvent event) {
        this.queueManager.addPlayerToQueue(event.getPlayerId(), event.getRoundFormat());
        Player player = Bukkit.getPlayer(event.getPlayerId());
        if (player == null) {
            return;
        }
        player.sendMessage(ChatColor.GREEN + "You have been queued!");
    }

}
    /*
            List<RoundQueue> roundQueue = event.getRoundQueue();

            if (!roundQueue.isEmpty()) {

                for (RoundQueue queue : roundQueue) {
                    if (queue.round() instanceof FFARound) {
                        this.queueManager.addPlayerToRound(event.getPlayerId(), queue.round().getId());
                        return;
                    }

                    for (Team team : queue.round().getTeams()) {
                        if (team.isFull()) continue;
                        this.queueManager.addPlayerToRound(event.getPlayerId(), queue.round().getId());
                    }
                }
            } else {
                final RoundQueue[] newQueue = new RoundQueue[1];
                CourseDifficulty difficulty = CourseDifficulty.HARD;

                if (event.getRoundType().equalsIgnoreCase("ffa")) {

                    World world = Bukkit.createWorld(new EmptyWorldCreator("Test_Course_FFA"));

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        CourseGrid courseGrid = new CourseGrid(8, 8, List.of(TileTypes.FAIRWAY, TileTypes.OBSTACLE), world);

                        newQueue[0] = this.queueManager.createQueue(
                                new FFARound(plugin, NDGManager.getSettings().getPlayerDataManager(),
                                new CourseImpl(difficulty, "New Course FFA", 18,
                                        courseGrid, world.getSpawnLocation(), Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap()), false, "ffa", 4));

                        courseGrid.generate(NDGManager.getSettings().getObstacleRegistry(), difficulty);

                        this.queueManager.addPlayerToRound(event.getPlayerId(), newQueue[0].round().getId());

                        for (int x = 0; x < courseGrid.getWidth(); x++) {
                            for (int z = 0; z < courseGrid.getDepth(); z++) {
                                Tile tile = courseGrid.getTile(x, z);
                                placeTileInWorld(world, tile.getLocation(), tile.getCollapsedState());
                            }
                        }
                    }, 60);

                } else if (event.getRoundType().equalsIgnoreCase("doubles")) {
                    World world = Bukkit.createWorld(new EmptyWorldCreator("Test_Course_Doubles"));

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        CourseGrid courseGrid = new CourseGrid(8, 8, List.of(TileTypes.FAIRWAY, TileTypes.OBSTACLE), world);

                        newQueue[0] = this.queueManager.createQueue(
                                new DoublesRound(plugin, NDGManager.getSettings().getPlayerDataManager(),
                                new CourseImpl(difficulty, "New Course Doubles", 18,
                                        courseGrid, world.getSpawnLocation(), Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap()), false, "doubles", 4));

                        courseGrid.generate(NDGManager.getSettings().getObstacleRegistry(), difficulty);

                        this.queueManager.addPlayerToRound(event.getPlayerId(), newQueue[0].round().getId());
                        for (int x = 0; x < courseGrid.getWidth(); x++) {
                            for (int z = 0; z < courseGrid.getDepth(); z++) {
                                Tile tile = courseGrid.getTile(x, z);
                                placeTileInWorld(world, tile.getLocation(), tile.getCollapsedState());
                            }
                        }

                    }, 60);
                }
            }

    private void placeTileInWorld(World world, Location location, TileTypes type) {
        int startX = location.getBlockX();
        int startZ = location.getBlockZ();
        int y = location.getBlockY();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Location blockLocation = new Location(world, startX + x, y, startZ + z);
                switch (type) {
                    case FAIRWAY -> world.getBlockAt(blockLocation).setType(Material.GRASS_BLOCK);

                    case OBSTACLE -> world.getBlockAt(blockLocation).setType(Material.STONE);

                    case WATER -> world.getBlockAt(blockLocation).setType(Material.WATER);

                    case TEE -> world.getBlockAt(blockLocation).setType(Material.GREEN_STAINED_GLASS);

                    case PIN -> world.getBlockAt(blockLocation).setType(Material.PINK_STAINED_GLASS);

                    case OUT_OF_BOUNDS -> world.getBlockAt(blockLocation).setType(Material.BLACK_STAINED_GLASS);

                }
            }
        }
    }

*/

