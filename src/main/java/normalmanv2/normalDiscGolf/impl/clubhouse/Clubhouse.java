package normalmanv2.normalDiscGolf.impl.clubhouse;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.DiscFlightEngine;
import normalmanv2.normalDiscGolf.impl.disc.DiscFlightResolver;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.gui.PracticeMenu;
import normalmanv2.normalDiscGolf.impl.manager.flight.DiscFlightManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class Clubhouse implements Listener {
    private record Trial(DiscModel disc, int distance) {}

    private final NormalDiscGolfPlugin plugin;
    private final Map<UUID, Trial> trials = new HashMap<>();
    private final Set<UUID> flying = new HashSet<>();
    private ClubhouseSite site;

    public Clubhouse(NormalDiscGolfPlugin plugin) { this.plugin = plugin; }

    public void start() {
        if (!plugin.getConfig().getBoolean("clubhouse.enabled", true)) return;
        var file = plugin.getDataFolder().toPath().resolve("clubhouse-site.yml");
        var yaml = new YamlConfiguration();
        try {
            if (Files.exists(file)) {
                yaml.load(file.toFile());
                var world = Bukkit.getWorld(UUID.fromString(yaml.getString("world", "")));
                if (world == null || !yaml.isInt("x") || !yaml.isInt("y") || !yaml.isInt("z"))
                    throw new IOException("Saved clubhouse world or position is unavailable");
                site = new ClubhouseSite(world, yaml.getInt("x"), yaml.getInt("y"), yaml.getInt("z"));
            } else {
                var world = Bukkit.getWorlds().stream().filter(candidate -> candidate.getEnvironment() == World.Environment.NORMAL)
                        .findFirst().orElseThrow(() -> new IOException("No overworld is loaded"));
                site = ClubhouseSite.find(world);
                if (site == null) throw new IOException("No space for a clubhouse near spawn");
                yaml.set("world", world.getUID().toString());
                yaml.set("x", site.x());
                yaml.set("y", site.y());
                yaml.set("z", site.z());
                saveSite(file, yaml);
            }
            if (!yaml.getBoolean("built")) {
                site.build();
                yaml.set("built", true);
                saveSite(file, yaml);
                plugin.getLogger().info("Built the clubhouse near spawn in " + site.world().getName()
                        + " at " + site.x() + ", " + site.y() + ", " + site.z());
            }
            refreshBoard();
            Bukkit.getScheduler().runTaskTimer(plugin, this::refreshBoard, 1200, 1200);
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException failure) {
            site = null;
            plugin.getLogger().log(Level.WARNING, "Could not set up the clubhouse", failure);
        }
    }

    private void saveSite(Path file, YamlConfiguration yaml) throws IOException {
        Path pending = Files.createTempFile(file.getParent(), "clubhouse-", ".tmp");
        try {
            yaml.save(pending.toFile());
            try {
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    public boolean available() { return site != null; }

    public void visit(Player player) {
        if (!canVisit(player)) return;
        leavePractice(player.getUniqueId());
        if (player.teleport(site.entrance())) {
            refreshBoard();
            player.sendMessage(ChatColor.GOLD + "Parkland Clubhouse · Pro Shop, Daily leaderboard, and putting green.");
        }
    }

    public void openPractice(Player player) {
        if (canVisit(player)) PracticeMenu.open(player, 0);
    }

    public void tryDisc(Player player, DiscModel model) {
        tryDisc(player, model, 0);
    }

    public void tryDisc(Player player, DiscModel model, int distance) {
        Trial trial = trials.get(player.getUniqueId());
        takeTee(player, model, distance != 0 ? distance : trial == null ? 8 : trial.distance());
    }

    public void takeTee(Player player, int distance) {
        Trial trial = trials.get(player.getUniqueId());
        if (trial == null) {
            if (canVisit(player)) PracticeMenu.open(player, distance);
        } else takeTee(player, trial.disc(), distance);
    }

    private void takeTee(Player player, DiscModel model, int distance) {
        if (!canVisit(player)) return;
        if (distance != 4 && distance != 8 && distance != 12) return;
        leavePractice(player.getUniqueId());
        player.closeInventory();
        if (!player.teleport(site.tee(distance))) return;
        trials.put(player.getUniqueId(), new Trial(model, distance));
        player.sendMessage(ChatColor.AQUA + "Trying " + model.displayName() + " from " + distance + " blocks.");
        player.sendMessage(ChatColor.GRAY + "Right-click a tee sign or use /throwdisc to aim again. Use /ndg practice to change discs.");
        aim(player, new String[0]);
    }

    public boolean aim(Player player, String[] args) {
        UUID id = player.getUniqueId();
        Trial trial = trials.get(id);
        if (trial == null) return false;
        if (!atTee(player)) {
            player.sendMessage(ChatColor.YELLOW + "Return to a practice tee with /ndg practice tee " + trial.distance() + ".");
            return true;
        }
        if (flying.contains(id)) {
            player.sendMessage(ChatColor.YELLOW + "Wait for your practice disc to land.");
            return true;
        }
        if (args.length > 0 && !args[0].equalsIgnoreCase(trial.disc().id())) {
            player.sendMessage(ChatColor.YELLOW + "Choose a trial disc with /ndg practice.");
            return true;
        }
        String technique = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "backhand";
        if (!NDGManager.getInstance().getThrowTechniqueRegistry().getBackingMap().containsKey(technique)) {
            player.sendMessage(ChatColor.RED + "Unknown throw technique. Use backhand or forehand.");
            return true;
        }
        ThrowMechanicImpl.forPractice(id, plugin, trial.disc().createDisc(), technique);
        player.sendMessage(ChatColor.GREEN + "Practice meter started for " + trial.disc().displayName() + ".");
        return true;
    }

    public boolean atTee(Player player) {
        Trial trial = trials.get(player.getUniqueId());
        return trial != null && site != null && player.getWorld().equals(site.world())
                && player.getLocation().distanceSquared(site.tee(trial.distance())) <= 2.25
                && DiscFlightResolver.findActiveRound(player) == null;
    }

    public void preview(Player player, ThrowMechanicImpl mechanic) {
        if (site != null) DiscFlightEngine.previewPractice(player, mechanic, site.basket(), site.flightBounds());
    }

    public boolean release(Player player, ThrowMechanicImpl mechanic) {
        UUID id = player.getUniqueId();
        if (!atTee(player) || !flying.add(id)) return false;
        Trial trial = trials.get(id);
        try {
            DiscFlightEngine.launchPractice(player, mechanic, site.basket(), site.flightBounds(), () ->
                    player.isOnline() && trials.get(id) == trial && player.getWorld().equals(site.world()), goal -> {
                flying.remove(id);
                if (!goal) {
                    player.sendMessage(ChatColor.GRAY + "Practice disc landed. Aim again from the tee.");
                    return;
                }
                var basket = site.basket();
                basket.getWorld().playSound(basket, Sound.BLOCK_CHAIN_HIT, .8f, 1.3f);
                basket.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, basket, 14, .35, .4, .35, .03);
                player.sendMessage(ChatColor.GREEN + "Practice basket! Aim again from the tee.");
            });
        } catch (RuntimeException failure) {
            flying.remove(id);
            plugin.getLogger().log(Level.WARNING, "Could not launch practice disc", failure);
            return false;
        }
        player.sendMessage(ChatColor.GREEN + "Practice disc released.");
        return true;
    }

    public boolean leavePractice(UUID id) {
        if (trials.remove(id) == null) return false;
        plugin.cancelMeter(id);
        if (flying.remove(id)) DiscFlightManager.cleanupPlayer(id);
        return true;
    }

    private boolean canVisit(Player player) {
        if (DiscFlightResolver.findActiveRound(player) != null) {
            player.sendMessage(ChatColor.RED + "Finish or leave your round before visiting the clubhouse.");
            return false;
        }
        if (site != null) return true;
        player.sendMessage(ChatColor.YELLOW + "The clubhouse is unavailable on this server.");
        return false;
    }

    public void refreshBoard() {
        if (site == null || !site.world().isChunkLoaded(site.boardChunkX(), site.boardChunkZ())) return;
        try {
            var board = NDGManager.getInstance().getDailyCompetition().publishedToday();
            String[] lines = {"§bDaily Top 3", "No scores yet", "", ""};
            if (board != null) {
                var standings = board.standings();
                for (int i = 0; i < Math.min(3, standings.size()); i++) {
                    var row = standings.get(i);
                    String name = row.entry().name();
                    lines[i + 1] = row.place() + " " + name.substring(0, Math.min(8, name.length())) + " " + row.entry().strokes();
                }
            }
            site.updateBoard(lines);
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not refresh the clubhouse Daily board", failure);
        }
    }

    @EventHandler public void loadChunk(ChunkLoadEvent event) {
        if (site != null && event.getWorld().equals(site.world())
                && event.getChunk().getX() == site.boardChunkX() && event.getChunk().getZ() == site.boardChunkZ())
            Bukkit.getScheduler().runTask(plugin, this::refreshBoard);
    }

    @EventHandler public void interact(PlayerInteractEvent event) {
        if (site == null || event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || plugin.released(event)) return;
        int station = site.station(event.getClickedBlock());
        if (station == 0) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        switch (station) {
            case -1 -> player.performCommand("ndg shop");
            case -2 -> { refreshBoard(); player.performCommand("ndg daily"); }
            case -3 -> openPractice(player);
            default -> takeTee(player, station);
        }
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        if (site != null && !event.getPlayer().hasPlayedBefore())
            event.getPlayer().sendMessage(ChatColor.AQUA + "Visit the disc golf clubhouse with /ndg clubhouse visit.");
    }

    @EventHandler public void quit(PlayerQuitEvent event) { leavePractice(event.getPlayer().getUniqueId()); }
    @EventHandler public void changedWorld(PlayerChangedWorldEvent event) { leavePractice(event.getPlayer().getUniqueId()); }
    @EventHandler public void breakBlock(BlockBreakEvent event) {
        if (site != null && site.owns(event.getBlock()) && !event.getPlayer().isOp()) event.setCancelled(true);
    }
    @EventHandler public void placeBlock(BlockPlaceEvent event) {
        if (site != null && site.owns(event.getBlock()) && !event.getPlayer().isOp()) event.setCancelled(true);
    }
    @EventHandler public void explosion(EntityExplodeEvent event) {
        if (site != null) event.blockList().removeIf(site::owns);
    }
    @EventHandler public void explosion(BlockExplodeEvent event) {
        if (site != null) event.blockList().removeIf(site::owns);
    }
}
