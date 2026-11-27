package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.ShotSetup.Stance;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Live and replay cameras share an anchor and restore each viewer independently. */
public final class RoundSpectating {
    private static final int MAX_FRAMES = 160;
    private static final int END_HOLD_TICKS = 12;
    private final RoundImpl round;
    private final Set<UUID> following = new HashSet<>();
    private final Set<UUID> replayOptOut = new HashSet<>();
    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private ArmorStand anchor;
    private Location discPosition;
    private Vector heading;
    private ItemStack liveItem;
    private List<Location> liveFrames;
    private boolean live;
    private ItemDisplay replayDisc;
    private BukkitTask replayTask;

    record Clip(ItemStack item, List<Location> frames, boolean putt) {}
    private record Viewer(Player player, Location origin, GameMode mode, boolean allowFlight, boolean flying) {}

    RoundSpectating(RoundImpl round) { this.round = round; }

    private NormalDiscGolfPlugin plugin() { return NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class); }
    public boolean cameraAvailable() { return plugin().getConfig().getBoolean("spectating.disc-camera", true); }
    public boolean replaysAvailable() { return plugin().getConfig().getBoolean("spectating.replays", true); }
    public boolean follows(UUID id) { return following.contains(id); }
    public boolean watchesReplays(UUID id) { return !replayOptOut.contains(id); }
    public boolean viewing(UUID id) { return viewers.containsKey(id); }
    public boolean replaying() { return replayTask != null; }

    public boolean permitsSpectatorTeleport(UUID id, Location to) {
        if (!viewing(id)) return true;
        return anchor != null && to != null && anchor.getWorld().equals(to.getWorld())
                && to.distanceSquared(anchor.getLocation()) < .25;
    }

    public void follow(Player player, boolean enabled) {
        UUID id = player.getUniqueId();
        if (enabled) {
            boolean added = following.add(id);
            if (added && live) attach(player);
        } else {
            following.remove(id);
            if (live) skip(id);
        }
    }

    public void watchReplays(UUID id, boolean enabled) {
        if (enabled) replayOptOut.remove(id);
        else {
            replayOptOut.add(id);
            if (replaying()) skip(id);
        }
    }

    public void beginFlight(Player thrower, ItemDisplay disc) {
        live = true;
        discPosition = disc.getLocation();
        heading = horizontal(thrower.getLocation().getDirection());
        if (replaysAvailable()) liveItem = disc.getItemStack().clone();
        if (liveItem != null || round instanceof DailyRound) {
            liveFrames = new ArrayList<>();
            liveFrames.add(discPosition.clone());
        }
        if (cameraAvailable()) {
            for (UUID id : following) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) attach(player);
            }
        }
    }

    public void flightFrame(Location position) {
        if (!live) return;
        if (liveFrames != null) {
            if (liveFrames.size() < MAX_FRAMES) liveFrames.add(position.clone());
            else liveFrames = null;
        }
        move(position);
    }

    List<Location> recordedFrames() { return liveFrames == null ? List.of() : liveFrames; }

    Clip finishFlight(Stance stance, boolean goal) {
        Clip clip = goal && liveItem != null && liveFrames != null && liveFrames.size() > 1
                ? new Clip(liveItem, List.copyOf(liveFrames), stance == Stance.PUTT) : null;
        abortFlight();
        return clip;
    }

    public void abortFlight() {
        if (!live) return;
        closeViewers();
        live = false;
        liveItem = null;
        liveFrames = null;
    }

    boolean playHighlight(Player scorer, ScoreCard.Basket basket, Clip clip, Runnable finished) {
        if (clip == null || basket == null || !replaysAvailable()) return false;
        double minimum = plugin().getConfig().getDouble("spectating.long-putt-blocks", 10);
        minimum = Double.isFinite(minimum) ? Math.clamp(minimum, 1, 15) : 10;
        if (!basket.ace() && (!clip.putt() || basket.distance() < minimum)) return false;
        String highlight = basket.ace() ? "Ace!" : "Long putt (" + Math.round(basket.distance()) + " blocks)";
        List<Location> frames = clip.frames();
        discPosition = frames.getFirst();
        heading = horizontal(frames.get(1).toVector().subtract(discPosition.toVector()));
        for (var team : round.getTeams()) for (UUID id : team.getTeamMembers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && watchesReplays(id) && attach(player)) {
                player.sendMessage(ChatColor.AQUA + "Instant replay: " + scorer.getName() + " - " + highlight);
                player.sendTitle(ChatColor.AQUA + "Instant replay", scorer.getName() + " - " + highlight, 3, 32, 8);
            }
        }
        if (viewers.isEmpty()) return false;
        replayDisc = discPosition.getWorld().spawn(discPosition, ItemDisplay.class, display -> {
            display.setItemStack(clip.item());
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setGlowColorOverride(Color.AQUA);
            display.setGlowing(true);
            display.setTeleportDuration(1);
            display.setPersistent(false);
        });
        int duration = Math.clamp(frames.size() - 1, 20, 60);
        replayTask = new BukkitRunnable() {
            private int elapsed;

            @Override public void run() {
                if (round.isOver()) { dispose(); return; }
                try {
                    move(frameAt(frames, Math.min(++elapsed, duration) / (double) duration));
                    if (!viewers.isEmpty() && elapsed < duration + END_HOLD_TICKS) {
                        if (elapsed <= duration) {
                            replayDisc.teleport(discPosition);
                            discPosition.getWorld().spawnParticle(Particle.END_ROD, discPosition, 1, 0, 0, 0, 0);
                        }
                        if (elapsed == duration)
                            discPosition.getWorld().playSound(discPosition, Sound.BLOCK_CHAIN_HIT, .8f, 1.35f);
                        return;
                    }
                } catch (RuntimeException failure) {
                    plugin().getLogger().log(Level.WARNING, "Could not play replay in " + round.getId(), failure);
                }
                stopReplay();
                finished.run();
            }
        }.runTaskTimer(plugin(), 1, 1);
        return true;
    }

    private static Location frameAt(List<Location> frames, double fraction) {
        double index = fraction * (frames.size() - 1);
        int first = (int) index;
        Location from = frames.get(first);
        Location to = frames.get(Math.min(first + 1, frames.size() - 1));
        return from.clone().add(to.toVector().subtract(from.toVector()).multiply(index - first));
    }

    private boolean attach(Player player) {
        if (player.getWorld() != discPosition.getWorld() || player.getGameMode() != GameMode.ADVENTURE
                || viewers.containsKey(player.getUniqueId())) return false;
        if (anchor == null) {
            Location camera = cameraPosition(discPosition);
            anchor = camera.getWorld().spawn(camera, ArmorStand.class, stand -> {
                stand.setMarker(true);
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setPersistent(false);
            });
        }
        viewers.put(player.getUniqueId(), new Viewer(player, player.getLocation(), player.getGameMode(),
                player.getAllowFlight(), player.isFlying()));
        player.setGameMode(GameMode.SPECTATOR);
        if (player.getGameMode() != GameMode.SPECTATOR || !player.teleport(anchor.getLocation())) {
            skip(player.getUniqueId());
            return false;
        }
        player.setSpectatorTarget(anchor);
        if (live) player.sendMessage(ChatColor.GRAY + "Disc camera attached. Sneak or use /ndg skip to exit.");
        return true;
    }

    private void move(Location position) {
        Vector movement = position.toVector().subtract(discPosition.toVector()).setY(0);
        if (movement.lengthSquared() > .008) {
            Vector next = heading.clone().multiply(.7).add(movement.normalize().multiply(.3));
            if (next.lengthSquared() > .001) heading = next.normalize();
        }
        discPosition = position;
        for (Viewer viewer : List.copyOf(viewers.values())) {
            Player player = viewer.player();
            if (!player.isOnline() || player.getGameMode() != GameMode.SPECTATOR
                    || player.getWorld() != position.getWorld() || !anchor.equals(player.getSpectatorTarget()))
                skip(player.getUniqueId());
        }
        if (anchor != null) anchor.teleport(cameraPosition(position));
    }

    private Location cameraPosition(Location target) {
        Location look = target.clone().add(0, .12, 0);
        Vector trailing = heading.clone().multiply(-3.4).setY(1.55);
        double length = trailing.length();
        Vector direction = trailing.clone().normalize();
        var obstruction = look.getWorld().rayTraceBlocks(look, direction, length, FluidCollisionMode.NEVER, true);
        if (obstruction != null) {
            double clearance = obstruction.getHitPosition().distance(look.toVector()) - .22;
            if (clearance < .24)
                return anchor != null ? anchor.getLocation().setDirection(look.toVector().subtract(anchor.getLocation().toVector()))
                        : look.clone().setDirection(heading);
            trailing = direction.multiply(clearance);
        }
        return look.add(trailing).setDirection(trailing.multiply(-1));
    }

    private static Vector horizontal(Vector direction) {
        direction.setY(0);
        return direction.lengthSquared() > .001 ? direction.normalize() : new Vector(0, 0, 1);
    }

    public void skip(UUID id) {
        Viewer viewer = viewers.remove(id);
        if (viewer == null) return;
        Player player = viewer.player();
        boolean inSession = player.getWorld().equals(viewer.origin().getWorld())
                || NDGManager.getInstance().getPlayerSessionManager().getRoundId(id).filter(round.getId()::equals).isPresent();
        if (player.getGameMode() == GameMode.SPECTATOR && inSession) {
            player.setSpectatorTarget(null);
            player.setGameMode(viewer.mode());
            player.setAllowFlight(viewer.allowFlight());
            player.setFlying(viewer.allowFlight() && viewer.flying());
            player.teleport(viewer.origin());
            if (!live) player.resetTitle();
        }
        if (viewers.isEmpty() && anchor != null) {
            anchor.remove();
            anchor = null;
        }
    }

    void removePlayer(UUID id) {
        skip(id);
        following.remove(id);
        replayOptOut.remove(id);
    }

    private void closeViewers() {
        for (UUID id : List.copyOf(viewers.keySet())) skip(id);
    }

    private void stopReplay() {
        if (replayTask != null) replayTask.cancel();
        replayTask = null;
        closeViewers();
        if (replayDisc != null) replayDisc.remove();
        replayDisc = null;
    }

    public void dispose() {
        abortFlight();
        stopReplay();
        following.clear();
        replayOptOut.clear();
    }
}
