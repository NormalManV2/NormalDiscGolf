package normalmanv2.normalDiscGolf.impl.course.event;

import normalmanv2.normalDiscGolf.impl.disc.HoleWind;
import normalmanv2.normalDiscGolf.impl.round.RoundFormat;
import normalmanv2.normalDiscGolf.impl.round.CompetitionRound;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import normalmanv2.normalDiscGolf.impl.round.RoundPresentation;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Applies the current hole's conditions to its course world and participants. */
public final class RoundCourseEvents {
    private static final int FOG_EFFECT_TICKS = 60;
    private final RoundImpl round;
    private final Map<Block, BlockData> lights = new HashMap<>();
    private final Set<UUID> fogged = new HashSet<>();
    private CourseEventSettings settings;
    private CourseEvent current = CourseEvent.CLEAR;
    private World world;
    private Location stormFlash;
    private int stormSeconds;

    public RoundCourseEvents(RoundImpl round) { this.round = round; }

    public CourseEvent current() { return current; }

    public HoleWind startHole(ConfigurationSection config, World courseWorld, int hole) {
        long seed = round.getCourse().seed();
        if (round instanceof CompetitionRound competition)
            return competition.wind() ? HoleWind.forHole(seed, hole) : HoleWind.CALM;
        if (round.getCourse().isCustom())
            return config.getBoolean("flight.wind", true) ? HoleWind.forHole(seed, hole) : HoleWind.CALM;
        if (settings == null) settings = CourseEventSettings.read(config);
        RoundFormat format = RoundFormat.of(round);
        CourseEvent next = settings.forHole(seed, hole, format != null && format.countsForStrokeRating());
        CourseEvent previous = current;
        if (next != previous) clearVisuals();
        world = courseWorld;
        current = next;
        switch (current) {
            case THUNDERSTORM -> {
                if (previous != current) {
                    world.setClearWeatherDuration(0);
                    world.setStorm(true);
                    world.setThundering(true);
                    world.setWeatherDuration(Integer.MAX_VALUE);
                    world.setThunderDuration(Integer.MAX_VALUE);
                    stormSeconds = 3;
                }
                if (settings.lightningFlashes())
                    stormFlash = round.getCourse().basket(world, hole).add(8, 1, 8);
            }
            case NIGHT_GOLF -> {
                clearLights();
                world.setTime(18000);
                light(round.getCourse().tee(world, hole).add(0, 2.1, 0));
                light(round.getCourse().basket(world, hole).add(0, 2.3, 0));
            }
            case FOG -> forEachPlayer(this::refreshFog);
            case CLEAR -> {}
        }
        if (current != CourseEvent.CLEAR || previous != CourseEvent.CLEAR)
            round.play().message(ChatColor.AQUA + current.status());
        if (!config.getBoolean("flight.wind", true)) return HoleWind.CALM;
        return current == CourseEvent.THUNDERSTORM
                ? HoleWind.thunderstorm(seed, hole, settings.stormWindScale()) : HoleWind.forHole(seed, hole);
    }

    public void tick() {
        if (current == CourseEvent.FOG) forEachPlayer(this::refreshFog);
        if (stormFlash != null && --stormSeconds <= 0) {
            world.strikeLightningEffect(stormFlash);
            stormSeconds = 12;
        }
    }

    private void forEachPlayer(Consumer<Player> action) {
        for (var team : round.getTeams()) for (UUID id : team.getTeamMembers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.getWorld().equals(world)) action.accept(player);
        }
    }

    private void refreshFog(Player player) {
        PotionEffect effect = player.getPotionEffect(PotionEffectType.DARKNESS);
        if (effect == null || fogged.contains(player.getUniqueId()) && isFogEffect(effect)) {
            if (player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, FOG_EFFECT_TICKS, 0, true, false, false)))
                fogged.add(player.getUniqueId());
        } else {
            fogged.remove(player.getUniqueId());
        }
        if (!RoundPresentation.isPreviewing(player))
            player.spawnParticle(Particle.CLOUD, player.getLocation().add(0, .35, 0), 5, 4, .35, 4, .006);
    }

    private static boolean isFogEffect(PotionEffect effect) {
        return effect != null && !effect.isInfinite() && effect.getDuration() <= FOG_EFFECT_TICKS
                && effect.getAmplifier() == 0 && effect.isAmbient() && !effect.hasParticles() && !effect.hasIcon();
    }

    public void removePlayer(UUID id) {
        if (!fogged.remove(id)) return;
        Player player = Bukkit.getPlayer(id);
        if (player != null && player.getWorld().equals(world) && isFogEffect(player.getPotionEffect(PotionEffectType.DARKNESS)))
            player.removePotionEffect(PotionEffectType.DARKNESS);
    }

    private void light(Location location) {
        Block block = location.getBlock();
        if (!block.getType().isAir()) return;
        Light data = (Light) Material.LIGHT.createBlockData();
        data.setLevel(15);
        lights.put(block, block.getBlockData());
        block.setBlockData(data, false);
    }

    private void clearVisuals() {
        for (UUID id : List.copyOf(fogged)) removePlayer(id);
        clearLights();
        if (world != null && current == CourseEvent.THUNDERSTORM) {
            world.setThundering(false);
            world.setStorm(false);
        }
        if (world != null && current == CourseEvent.NIGHT_GOLF) world.setTime(6000);
        stormFlash = null;
    }

    private void clearLights() {
        lights.forEach((block, original) -> {
            if (block.getType() == Material.LIGHT) block.setBlockData(original, false);
        });
        lights.clear();
    }

    public void dispose() {
        clearVisuals();
        current = CourseEvent.CLEAR;
        world = null;
    }
}
