package normalmanv2.normalDiscGolf.impl.gui.round;

import normalmanv2.normalDiscGolf.impl.util.Bounds;
import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.common.button.Button;
import normalmanv2.normalDiscGolf.common.item.ItemBuilder;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGenerationProfile;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseTerrain;
import normalmanv2.normalDiscGolf.impl.course.grid.HoleArchetype;
import normalmanv2.normalDiscGolf.impl.gui.InventoryGUI;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import normalmanv2.normalDiscGolf.impl.round.RoundFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class CourseLobbiesGUI extends InventoryGUI {
    private static final int ROOMS_PER_PAGE = 36;
    private final RoundFormat format;
    private final RoundQueueManager queue = NDGManager.getInstance().getRoundQueueManager();
    private List<RoundQueueManager.Lobby> displayed;
    private boolean queued;
    private int page;
    private CourseTerrain selectedTerrain;
    private int presetPage;
    private BukkitTask refresh;

    CourseLobbiesGUI(RoundFormat format) {
        super(MenuSkin.LOBBIES);
        this.format = format;
    }

    @Override public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        redraw(player, true);
        refresh = Bukkit.getScheduler().runTaskTimer(NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class),
                () -> redraw(player, false), 20, 20);
    }

    @Override public void onClose(InventoryCloseEvent event) {
        if (refresh != null) refresh.cancel();
    }

    private void redraw(Player player, boolean force) {
        if (selectedTerrain != null) {
            if (force) drawPresets();
            return;
        }
        List<RoundQueueManager.Lobby> lobbies = queue.lobbies(format);
        boolean inQueue = queue.isPlayerQueued(player.getUniqueId());
        if (!force && lobbies.equals(displayed) && inQueue == queued) return;
        displayed = lobbies;
        queued = inQueue;
        int pages = Math.max(1, (lobbies.size() + ROOMS_PER_PAGE - 1) / ROOMS_PER_PAGE);
        page = Bounds.clamp(page, 0, pages - 1);
        buttonMap.clear();
        getInventory().clear();

        for (int i = page * ROOMS_PER_PAGE; i < Math.min(lobbies.size(), (page + 1) * ROOMS_PER_PAGE); i++) {
            var lobby = lobbies.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("&7" + holeCount(lobby.holes()) + " · Seed " + lobby.seed());
            if (lobby.preset() != null) lore.add("&bPreset: " + lobby.preset());
            lore.add("&e" + lobby.status());
            lore.add("&f" + lobby.players().size() + " / " + lobby.capacity() + " queued");
            lobby.players().forEach(name -> lore.add("&7" + name));
            lore.add("&aClick to join this lobby");
            button(i % ROOMS_PER_PAGE, icon(lobby.terrain()), "&a" + lobby.terrain().label() + " Lobby",
                    viewer -> queue(viewer, " " + lobby.id()), lore.toArray(String[]::new));
        }
        if (lobbies.isEmpty()) {
            getInventory().setItem(13, new ItemBuilder(Material.PAPER).setDisplayName("&7No open lobbies")
                    .setLore("&7Use Quick Join or create a course below.").build());
        }

        if (format != RoundFormat.DAILY) {
            List<CourseTerrain> terrains = queue.availableTerrains();
            for (int i = 0; i < terrains.size(); i++) {
                CourseTerrain terrain = terrains.get(i);
                boolean hasPresets = queue.lobbyProfiles(terrain).stream().anyMatch(profile -> profile.id() != null);
                button(36 + i, icon(terrain), "&bNew " + terrain.label() + " Lobby",
                        viewer -> selectTerrain(viewer, terrain), hasPresets ? "&7Choose a generation preset."
                                : "&7Use the server's course settings.");
            }
        }
        button(45, Material.ARROW, "&eBack to modes",
                viewer -> NDGManager.getInstance().getGuiManager().openGUI(new RoundPickerGUI(), viewer));
        if (page > 0) button(46, Material.ARROW, "&ePrevious page", viewer -> { page--; redraw(viewer, true); });
        if (queued) button(47, Material.BARRIER, "&cLeave queue", viewer -> {
            viewer.performCommand("ndg leave");
            redraw(viewer, true);
        });
        button(48, Material.EMERALD, "&aQuick Join", viewer -> queue(viewer, ""),
                "&7Join a match or create a lobby if needed.");
        button(49, Material.COMPASS, "&bNew Lobby", viewer -> queue(viewer, " new"),
                format == RoundFormat.DAILY ? "&7Start a new lobby on today's course." : "&7Use the server's course settings.");
        getInventory().setItem(50, new ItemBuilder(Material.BOOK).setDisplayName("&e" + format.label())
                .setLore("&7Page " + (page + 1) + " / " + pages).build());
        if (page + 1 < pages) button(52, Material.ARROW, "&eNext page", viewer -> { page++; redraw(viewer, true); });
        populateInventory();
    }

    private void selectTerrain(Player player, CourseTerrain terrain) {
        var profiles = queue.lobbyProfiles(terrain);
        if (profiles.size() == 1 && profiles.get(0).id() == null) {
            queue(player, " new " + terrain.id());
            return;
        }
        selectedTerrain = terrain;
        presetPage = 0;
        drawPresets();
    }

    private void drawPresets() {
        var terrain = selectedTerrain;
        var profiles = queue.lobbyProfiles(terrain);
        int pages = Math.max(1, (profiles.size() + ROOMS_PER_PAGE - 1) / ROOMS_PER_PAGE);
        presetPage = Bounds.clamp(presetPage, 0, pages - 1);
        buttonMap.clear();
        getInventory().clear();
        getInventory().setItem(4, new ItemBuilder(icon(terrain)).setDisplayName("&b" + terrain.label() + " Presets")
                .setLore("&7Choose a preset for this lobby.").build());
        if (profiles.isEmpty()) getInventory().setItem(22, new ItemBuilder(Material.PAPER)
                .setDisplayName("&7No presets available").build());

        for (int index = presetPage * ROOMS_PER_PAGE; index < Math.min(profiles.size(), (presetPage + 1) * ROOMS_PER_PAGE); index++) {
            CourseGenerationProfile profile = profiles.get(index);
            var settings = profile.settings(terrain);
            Long seed = profile.fixedSeed();
            String selection = " new " + terrain.id() + (profile.id() == null ? "" : " " + profile.id());
            var types = settings.archetypes().stream().map(HoleArchetype::label).toList();
            String holeTypes = types.size() <= 3 ? String.join(", ", types) : types.size() + " hole types";
            List<String> lore = new ArrayList<>(List.of(
                    "&7" + holeCount(queue.lobbyHoles(format, profile)) + " · " + (seed == null ? "Random seed" : "Seed " + seed),
                    "&7Fairway " + settings.fairwayWidth() + " blocks · Elevation " + settings.elevation(),
                    "&7Obstacles " + Math.round(settings.obstacleDensity() * 100) + "% · Hazards "
                            + Math.round(settings.hazardDensity() * 100) + "%",
                    "&7" + holeTypes));
            if (profile.id() != null) lore.add("&8" + profile.id());
            button(9 + index % ROOMS_PER_PAGE, profile.id() == null ? Material.COMPASS : Material.BOOK,
                    "&a" + profile.label(), viewer -> queue(viewer, selection), lore.toArray(String[]::new));
        }
        button(45, Material.ARROW, "&eBack to lobbies", viewer -> {
            selectedTerrain = null;
            redraw(viewer, true);
        });
        if (presetPage > 0) button(46, Material.ARROW, "&ePrevious page", viewer -> { presetPage--; drawPresets(); });
        if (pages > 1) getInventory().setItem(50, new ItemBuilder(Material.PAPER)
                .setDisplayName("&ePage " + (presetPage + 1) + " / " + pages).build());
        if (presetPage + 1 < pages) button(52, Material.ARROW, "&eNext page", viewer -> { presetPage++; drawPresets(); });
        populateInventory();
    }

    private static String holeCount(int holes) { return holes + (holes == 1 ? " hole" : " holes"); }

    private void queue(Player player, String selection) {
        player.performCommand("ndg queue " + format.id() + selection);
        if (queue.isPlayerQueued(player.getUniqueId())) player.closeInventory();
        else redraw(player, true);
    }

    private void button(int slot, Material icon, String title, Consumer<Player> action, String... lore) {
        addButton(slot, new Button(new ItemBuilder(icon).setDisplayName(title).setLore(lore).build()) {
            @Override public void onClick(InventoryClickEvent event) { RoundPickerGUI.afterClick(event, action); }
        });
    }

    private static Material icon(CourseTerrain terrain) {
        return switch (terrain) {
            case PARKLAND -> Material.GRASS_BLOCK;
            case WOODLAND -> Material.SPRUCE_SAPLING;
            case MOUNTAIN -> Material.SNOW_BLOCK;
            case DESERT -> Material.RED_SAND;
            case ISLAND -> Material.HEART_OF_THE_SEA;
            case NETHER -> Material.CRIMSON_FUNGUS;
            case SKY -> Material.FEATHER;
        };
    }
}
