package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.cosmetic.ScoreDecal;
import normalmanv2.normalDiscGolf.impl.course.event.RoundCourseEvents;
import normalmanv2.normalDiscGolf.impl.course.custom.CustomCourse;
import normalmanv2.normalDiscGolf.impl.course.custom.CustomHoleMarkers;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.disc.DiscTrajectory;
import normalmanv2.normalDiscGolf.impl.disc.HoleWind;
import normalmanv2.normalDiscGolf.impl.disc.ShotSetup;
import normalmanv2.normalDiscGolf.impl.disc.ShotSetup.Stance;
import normalmanv2.normalDiscGolf.impl.event.GoalScoreEvent;
import normalmanv2.normalDiscGolf.impl.listener.RoundPlayerListener;
import normalmanv2.normalDiscGolf.impl.manager.flight.DiscFlightManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills.PracticeContext;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.player.score.RoundAnalytics;
import normalmanv2.normalDiscGolf.impl.round.PairedShotTurn.Outcome;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Server-owned lies, shot state and turn resolution. */
public final class RoundPlayState {
    private static final PlayerSkills BASELINE_SKILLS = new PlayerSkills(1, 1);
    private final RoundImpl round;
    private final RoundFormat format;
    private final RoundCourseEvents courseEvents;
    private final RoundSpectating spectating;
    private final CustomHoleMarkers customMarkers = new CustomHoleMarkers();
    private final Map<Team, Location> lies = new HashMap<>();
    private final Map<Team, UUID> lastThrower = new HashMap<>();
    private final Map<UUID, List<DiscModel>> roundBags = new HashMap<>();
    private final Map<UUID, DiscModel> lockedDiscs = new HashMap<>();
    private RouletteDraw rouletteDraw;
    private final Map<UUID, ShotSetup> shotSetups = new HashMap<>();
    private final Map<UUID, RoundAnalytics> analytics = new HashMap<>();
    private HoleWind wind = HoleWind.CALM;
    private Shot shot;
    private PairedShotTurn pairedShotTurn;
    private BukkitTask basketCelebration;
    private long deadline;
    private int hole = -1;
    private HoleFlyover flyover;
    private final RoundHud hud;
    public record Shot(UUID player, int hole, Team team, Location origin, Location release, PracticeContext practice) {}
    private record RouletteDraw(UUID player, DiscModel disc) {}

    public RoundPlayState(RoundImpl round) {
        this.round = round;
        this.format = RoundFormat.of(round);
        this.courseEvents = new RoundCourseEvents(round);
        this.spectating = new RoundSpectating(round);
        this.hud = new RoundHud(round);
    }
    public boolean usesPairedAttempts() { return format != null && format.usesPairedAttempts(); }
    public boolean usesOneDisc() { return format == RoundFormat.ONE_DISC; }
    public boolean usesRoulette() { return format == RoundFormat.DISC_ROULETTE; }
    public boolean normalizesCareerSkills() {
        if (round instanceof CompetitionRound) return true;
        return format != null && !round.getCourse().isCustom() && format.countsForStrokeRating()
                && plugin().getConfig().getBoolean("career.normalize-rated-skills", true);
    }

    public PlayerSkills effectiveSkills(PlayerSkills personal) {
        return normalizesCareerSkills() ? BASELINE_SKILLS : personal;
    }
    private NormalDiscGolfPlugin plugin() { return NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class); }
    public Team team(UUID id) { return round.getTeams().stream().filter(t -> t.contains(id)).findFirst().orElse(null); }
    public Team currentTeam() { return round.getTeams().stream().filter(round::isTurn).findFirst().orElse(null); }
    public UUID thrower(Team team) {
        if (team == null) return null;
        if (usesPairedAttempts()) {
            if (pairedShotTurn != null && pairedShotTurn.team() == team)
                return pairedShotTurn.choosing() ? null : pairedShotTurn.nextThrower();
            return team.getTeamMembers().stream().findFirst().orElse(null);
        }
        List<UUID> members = new ArrayList<>(team.getTeamMembers());
        if (members.isEmpty()) return null;
        int previous = members.indexOf(lastThrower.get(team));
        return members.get((previous + 1) % members.size());
    }
    public Location lie(Team team) {
        Location location = lies.get(team);
        return location == null ? null : location.clone();
    }
    public boolean inFlight() { return shot != null; }
    public Team choosingLie() { return pairedShotTurn != null && pairedShotTurn.choosing() ? pairedShotTurn.team() : null; }
    public void setPackedDiscs(UUID player, List<DiscModel> discs) { roundBags.put(player, List.copyOf(discs)); }
    public List<DiscModel> packedDiscs(UUID player) { return roundBags.getOrDefault(player, List.of()); }
    public DiscModel lockedDisc(UUID player) { return lockedDiscs.get(player); }
    public boolean lockDisc(UUID player, DiscModel model) {
        return usesOneDisc() && !round.isOver() && round.getState() == RoundState.START && team(player) != null
                && packedDiscs(player).contains(model) && lockedDiscs.putIfAbsent(player, model) == null;
    }
    public List<DiscModel> throwableDiscs(UUID player) {
        if (!usesOneDisc() && !usesRoulette()) return packedDiscs(player);
        DiscModel disc = usesRoulette() ? rouletteDisc(player) : lockedDisc(player);
        return disc == null ? List.of() : List.of(disc);
    }
    public DiscModel rouletteDisc(UUID player) {
        return rouletteDraw != null && rouletteDraw.player().equals(player) ? rouletteDraw.disc() : null;
    }
    public String discRestriction(UUID player) {
        if (round instanceof DailyRound) return "The Daily Course uses the same three starter discs for everyone.";
        if (round instanceof WeeklyTournamentRound) return "The weekly tournament uses the same three starter discs for everyone.";
        if (usesRoulette()) {
            DiscModel draw = rouletteDisc(player);
            return draw == null ? "Disc Roulette draws a disc when your turn begins."
                    : "Disc Roulette assigned " + draw.displayName() + " for this throw.";
        }
        DiscModel locked = lockedDisc(player);
        return locked == null ? "That disc is not in your round bag. Use /ndg bag to see what you packed."
                : "Your disc is locked to " + locked.displayName() + " for this round.";
    }
    private void drawRouletteDisc(Player player) {
        UUID id = player.getUniqueId();
        if (rouletteDisc(id) != null) return;
        clearRouletteDraw();
        var packed = packedDiscs(id);
        if (packed.isEmpty()) return;
        DiscModel disc = packed.get(ThreadLocalRandom.current().nextInt(packed.size()));
        rouletteDraw = new RouletteDraw(id, disc);
        RoundPlayerListener.equipOnlyDisc(player, disc);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Disc Roulette drew " + disc.displayName() + ".");
    }
    private void clearRouletteDraw() {
        if (rouletteDraw == null) return;
        Player player = Bukkit.getPlayer(rouletteDraw.player());
        rouletteDraw = null;
        if (player != null) RoundPlayerListener.equipOnlyDisc(player, null);
    }
    public ShotSetup setup(UUID player) { return shotSetups.getOrDefault(player, ShotSetup.DEFAULT); }
    public void setSetup(UUID player, ShotSetup setup) {
        ShotSetup previous = setup(player);
        shotSetups.put(player, setup);
        if (!previous.withAngle(setup.angle()).equals(setup)) plugin().cancelMeter(player);
    }
    public HoleWind wind() { return wind; }
    public RoundAnalytics.Stats analytics(UUID player) {
        RoundAnalytics stats = analytics.get(player);
        return stats == null ? RoundAnalytics.Stats.EMPTY : stats.snapshot();
    }
    public RoundCourseEvents courseEvents() { return courseEvents; }
    public RoundSpectating spectating() { return spectating; }
    public double distanceToBasket(UUID player) {
        Location origin = lie(team(player));
        if (origin == null) return Double.POSITIVE_INFINITY;
        Location basket = round.getCourse().basket(origin.getWorld(), round.getCurrentHole());
        return Math.hypot(basket.getX() - origin.getX(), basket.getZ() - origin.getZ());
    }

    public PracticeContext practiceContext(UUID player) {
        Team team = team(player);
        Location origin = lie(team);
        if (origin == null) return PracticeContext.OPEN_LIE;
        TileTypes type = round.getCourse().tileAt(origin);
        boolean recovery = type == TileTypes.HEAVY_ROUGH || type == TileTypes.OBSTACLE || type == TileTypes.SAND;
        ScoreCard card = round.getScoreCardByTeam(team);
        boolean finalHole = round.getCourse().holes() > 1 && round.getCurrentHole() == round.getCourse().holes() - 1;
        boolean pressure = distanceToBasket(player) <= ShotSetup.PUTTING_RANGE
                && (finalHole || card != null && card.getCurrentStrokes() >= 2);
        return new PracticeContext(recovery, wind.strength() > 0, pressure);
    }
    public Shot shot() { return shot; }
    public void turnChanged() {
        turnChanged(true);
    }
    private void turnChanged(boolean preparePosition) {
        deadline = System.currentTimeMillis() + Math.max(10, plugin().getConfig().getInt("turn-timeout-seconds", 90)) * 1000L;
        if (preparePosition) RoundPresentation.prepareTurn(round);
        Team team = currentTeam();
        UUID id = thrower(team);
        Player player = id == null ? null : Bukkit.getPlayer(id);
        if (player != null) {
            if (usesRoulette()) drawRouletteDisc(player);
            String suffix = usesPairedAttempts() ? " • " + format.label()
                    : team.getMaximumPlayers() > 1 ? " • Alternate shot" : "";
            message("§e" + player.getName() + "'s turn" + suffix);
        }
    }
    public void startHole() {
        clearRouletteDraw();
        hole = round.getCurrentHole();
        flyover = null;
        World world = round.world();
        if (round.getCourse() instanceof CustomCourse custom) customMarkers.show(custom, world, hole);
        wind = courseEvents.startHole(plugin().getConfig(), world, hole);
        message(ChatColor.GRAY + wind.description());
        lies.clear();
        lastThrower.clear();
        pairedShotTurn = null;
        for (Team team : round.getTeams()) lies.put(team, round.getCourse().tee(world, hole));
        // The lifecycle positions every player (or starts the preview) immediately after this.
        // Avoid multiple conflicting teleports in the same tick at a hole transition.
        turnChanged(false);
    }

    HoleFlyover flyover(World world, int holeNumber) {
        if (flyover == null) flyover = new HoleFlyover(round.getCourse(), world, holeNumber);
        return flyover;
    }
    public String validate(Player player) {
        if (round.isOver() || round.getState() != RoundState.START) return "This round is not accepting throws.";
        if (RoundPresentation.isPreviewing(round)) return "Wait for the preview, or use /ndg skip.";
        if (spectating.replaying()) return "Wait for the instant replay, or use /ndg skip to exit your camera.";
        if (shot != null) return "Wait for the disc to land.";
        if (basketCelebration != null) return "Wait for the basket celebration.";
        Team team = team(player.getUniqueId());
        if (round instanceof SingleThrowRound single && team != null && single.hasFinishedHole(team))
            return "Your " + format.label() + " attempt is complete. Wait for the next hole.";
        if (round instanceof EliminationRound elimination && team != null && elimination.isSafe(team))
            return "You are safe this hole. Wait for the shootout to finish.";
        if (team == null || !round.isTurn(team)) return "It is not your team's turn.";
        if (choosingLie() == team) return "Choose a lie with /ndg lie 1 or /ndg lie 2.";
        if (!player.getUniqueId().equals(thrower(team))) return usesPairedAttempts()
                ? "Your teammate throws next in " + format.label() + "." : "Your teammate throws next (alternate shot).";
        if (usesOneDisc() && lockedDisc(player.getUniqueId()) == null)
            return "Choose your disc in /ndg bag before throwing.";
        if (usesRoulette() && rouletteDisc(player.getUniqueId()) == null)
            return discRestriction(player.getUniqueId());
        Location lie = lie(team);
        if (lie == null || player.getWorld() != lie.getWorld() || player.getLocation().distanceSquared(lie) > 2.25)
            return "Return to your lie with /ndg lie.";
        return null;
    }
    public boolean beginShot(Player player) {
        String error = validate(player);
        if (error != null) { player.sendMessage("§c" + error); return false; }
        Team team = team(player.getUniqueId());
        shot = new Shot(player.getUniqueId(), hole, team, lie(team), player.getLocation(), practiceContext(player.getUniqueId()));
        return true;
    }
    public void failedShot() { spectating.abortFlight(); shot = null; turnChanged(); }
    public void resolve(Player player, Location landing, boolean goal) {
        resolve(player, landing, goal, null, null);
    }

    public void resolve(Player player, Location landing, boolean goal, DiscType disc, String technique) {
        resolve(player, landing, goal, disc, technique, Stance.NORMAL, false, false);
    }

    public void resolve(Player player, DiscTrajectory.Step step, DiscType disc, String technique, boolean roller) {
        resolve(player, step, disc, technique, roller ? Stance.ROLLER : Stance.NORMAL);
    }

    public void resolve(Player player, DiscTrajectory.Step step, DiscType disc, String technique, Stance stance) {
        resolve(player, step.location(), step.goal(), disc, technique, stance, step.hazard(), step.expiredInAir());
    }

    private void resolve(Player player, Location landing, boolean goal, DiscType disc, String technique,
                         Stance stance, boolean hazardContact, boolean expiredInAir) {
        Shot completed = shot;
        if (completed == null || !completed.player().equals(player.getUniqueId()) || completed.hole() != round.getCurrentHole() || round.isOver()) return;
        if (round instanceof DailyRound daily) daily.ghosts().record(completed.player(), completed.hole(), spectating.recordedFrames());
        var clip = spectating.finishFlight(stance, goal);
        shot = null;
        clearRouletteDraw();
        lastThrower.put(completed.team(), completed.player());
        ScoreCard card = round.getScoreCardByTeam(completed.team());
        card.recordParticipation(completed.player());
        var type = round.getCourse().tileAt(landing);
        boolean hazard = !goal && (hazardContact || type.isHazard());
        recordAnalytics(completed, landing, stance, type, goal, hazard, expiredInAir);
        if (disc != null) awardPractice(player, disc, technique, hazard, goal, stance, completed.practice());
        if (usesPairedAttempts() && (!goal || format == RoundFormat.WORST_SHOT)) {
            recordPairedAttempt(player, completed, landing, goal, hazard, clip);
            return;
        }
        if (usesPairedAttempts()) {
            pairedShotTurn = null;
            card.trackStroke();
        }
        if (goal) {
            scoreBasket(player, card, landing.getWorld(), basketDistance(completed, landing.getWorld()), clip);
            return;
        } else if (round instanceof ClosestToPinRound closest && expiredInAir && !hazard) {
            closest.miss(completed.team(), "flight ended in the air");
        } else if (round instanceof SingleThrowRound single) {
            single.finishThrow(completed.team(), landing, hazard);
        } else {
            Location next = hazard ? completed.origin() : round.getCourse().safeLie(landing, completed.origin());
            if (hazard) {
                chargeObPenalty(card, completed.player());
                message("§cHazard or out of bounds. One penalty stroke. Replay the previous lie.");
            }
            lies.put(completed.team(), next);
            RoundPresentation.teleportTeamToLie(player, round, next);
            if (card.getCurrentStrokes() >= maxStrokes()) finishHole(completed.team(), "Stroke limit");
        }
        round.nextTurn();
    }

    private double basketDistance(Shot completed, World world) {
        Location basket = round.getCourse().basket(world, completed.hole());
        return Math.hypot(basket.getX() - completed.release().getX(), basket.getZ() - completed.release().getZ());
    }

    private void recordAnalytics(Shot completed, Location landing, Stance stance, TileTypes type,
                                 boolean goal, boolean hazard, boolean expiredInAir) {
        var course = round.getCourse();
        Location tee = course.tee(landing.getWorld(), completed.hole());
        boolean atTee = completed.origin().distanceSquared(tee) < .01;
        boolean fault = hazard || expiredInAir && round instanceof ClosestToPinRound;
        boolean fairway = !course.isCustom() && !expiredInAir && (type == TileTypes.FAIRWAY || type == TileTypes.PIN)
                && landing.getY() <= course.grid().surfaceHeight(landing.getBlockX(), landing.getBlockZ()) + 1.3;
        RoundAnalytics.Finish finish = goal ? RoundAnalytics.Finish.BASKET
                : fault ? RoundAnalytics.Finish.FAULT : fairway ? RoundAnalytics.Finish.FAIRWAY : RoundAnalytics.Finish.OTHER;
        double distance = goal ? basketDistance(completed, landing.getWorld())
                : Math.hypot(landing.getX() - completed.release().getX(), landing.getZ() - completed.release().getZ());
        analytics.computeIfAbsent(completed.player(), ignored -> new RoundAnalytics())
                .recordThrow(completed.hole(), atTee, stance == Stance.PUTT, finish, distance);
    }

    private void chargeObPenalty(ScoreCard card, UUID player) {
        card.addPenalty(1);
        analytics.computeIfAbsent(player, ignored -> new RoundAnalytics()).recordPenalty();
    }

    private void scoreBasket(Player player, ScoreCard card, World world, double distance, RoundSpectating.Clip clip) {
        boolean scoredBefore = card.getHoleScores().containsKey(hole);
        Bukkit.getPluginManager().callEvent(new GoalScoreEvent(player, hole, round, distance));
        ScoreCard.Basket result = card.getBaskets().get(hole);
        if (!scoredBefore && result != null && round instanceof DailyRound daily)
            daily.ghosts().announceHole(player.getUniqueId(), card, hole);
        if (scoredBefore || result == null || round.isOver() || world.getPlayers().isEmpty()) {
            round.nextTurn();
            return;
        }
        Location basket = round.getCourse().basket(world, hole);
        showDecal(player, basket);
        playBasketBeat(basket, result.ace(), 0);
        int duration = result.ace() ? 24 : 12;
        basketCelebration = new BukkitRunnable() {
            private int elapsed;

            @Override public void run() {
                elapsed += 4;
                if (!round.isOver() && elapsed < duration) {
                    playBasketBeat(basket, result.ace(), elapsed);
                    return;
                }
                cancel();
                basketCelebration = null;
                if (!round.isOver() && !spectating.playHighlight(player, result, clip, round::nextTurn)) round.nextTurn();
            }
        }.runTaskTimer(plugin(), 4L, 4L);
    }

    private void playBasketBeat(Location basket, boolean ace, int elapsed) {
        try {
            BasketCelebration.beat(basket, ace, elapsed);
        } catch (RuntimeException failure) {
            plugin().getLogger().log(java.util.logging.Level.WARNING, "Could not render basket celebration", failure);
        }
    }

    private void recordPairedAttempt(Player player, Shot completed, Location landing, boolean goal, boolean hazard,
                                     RoundSpectating.Clip clip) {
        Team team = completed.team();
        if (pairedShotTurn == null) pairedShotTurn = new PairedShotTurn(team);
        Location next = goal ? landing : hazard ? completed.origin() : round.getCourse().safeLie(landing, completed.origin());
        Outcome outcome = goal ? Outcome.BASKET : hazard ? Outcome.HAZARD : Outcome.LEGAL;
        pairedShotTurn.record(new PairedShotTurn.Attempt(completed.player(), player.getName(), next, outcome,
                goal ? basketDistance(completed, landing.getWorld()) : 0, clip));
        if (hazard) player.sendMessage(ChatColor.YELLOW + "That attempt landed in a hazard.");
        if (goal && pairedShotTurn.nextThrower() != null)
            message(ChatColor.GREEN + player.getName() + " reached the basket. Their teammate still has a throw.");
        continuePairedShotTurn(player);
    }

    private void continuePairedShotTurn(Player resolvingPlayer) {
        if (pairedShotTurn.nextThrower() != null) {
            turnChanged();
            return;
        }
        if (pairedShotTurn.choosing()) return;
        if (format == RoundFormat.WORST_SHOT) {
            var selected = pairedShotTurn.worst(choiceBasket());
            if (selected.outcome() == Outcome.BASKET) {
                Player scorer = resolvingPlayer != null && selected.player().equals(resolvingPlayer.getUniqueId())
                        ? resolvingPlayer : Bukkit.getPlayer(selected.player());
                if (scorer == null) return;
                ScoreCard card = round.getScoreCardByTeam(pairedShotTurn.team());
                pairedShotTurn = null;
                card.trackStroke();
                scoreBasket(scorer, card, selected.lie().getWorld(), selected.basketDistance(), selected.clip());
            } else {
                commitPairedLie(selected, null);
            }
            return;
        }
        var legal = pairedShotTurn.legalLies();
        if (legal.size() < 2) {
            commitPairedLie(pairedShotTurn.fallback(choiceBasket()), null);
            return;
        }
        pairedShotTurn.openChoice();
        int seconds = Math.max(5, plugin().getConfig().getInt("best-shot-choice-seconds", 20));
        deadline = System.currentTimeMillis() + seconds * 1000L;
        for (UUID id : pairedShotTurn.team().getTeamMembers()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            player.sendMessage(ChatColor.GOLD + "Choose a Best Shot lie within " + seconds + " seconds.");
            showLieOptions(player);
        }
        showLieMarkers();
    }

    private Location choiceBasket() {
        return round.getCourse().basket(lies.get(pairedShotTurn.team()).getWorld(), hole);
    }

    public boolean showLieOptions(Player player) {
        Team team = choosingLie();
        if (team == null || !team.contains(player.getUniqueId())) return false;
        Location basket = choiceBasket();
        var choices = pairedShotTurn.legalLies();
        for (int index = 0; index < choices.size(); index++) {
            var choice = choices.get(index);
            int number = index + 1;
            var button = new TextComponent("Lie " + number + " · " + choice.name() + " · "
                    + Math.round(choice.lie().distance(basket)) + " blocks to the basket");
            button.setColor(index == 0 ? net.md_5.bungee.api.ChatColor.AQUA : net.md_5.bungee.api.ChatColor.YELLOW);
            button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/ndg lie " + number + " " + pairedShotTurn.choiceId()));
            player.spigot().sendMessage(button);
        }
        player.sendMessage(ChatColor.GRAY + "Click a lie or use /ndg lie 1 or /ndg lie 2.");
        return true;
    }

    public void chooseLie(Player player, int number, String choiceId) {
        Team team = choosingLie();
        if (team == null || !team.contains(player.getUniqueId()) || !round.isTurn(team)) {
            player.sendMessage(ChatColor.RED + "Your team does not have a lie to choose right now.");
            return;
        }
        if (choiceId != null && !pairedShotTurn.choiceId().toString().equals(choiceId)) {
            player.sendMessage(ChatColor.RED + "That lie choice expired. Use /ndg lie to see the current options.");
            return;
        }
        var choices = pairedShotTurn.legalLies();
        if (number < 1 || number > choices.size()) {
            player.sendMessage(ChatColor.RED + "Choose lie 1 or 2.");
            return;
        }
        commitPairedLie(choices.get(number - 1), player.getName() + " selected lie " + number);
    }

    private void showLieMarkers() {
        var choices = pairedShotTurn.legalLies();
        for (int index = 0; index < choices.size(); index++) {
            var color = index == 0 ? Color.AQUA : Color.YELLOW;
            var marker = choices.get(index).lie().add(0, .85, 0);
            for (UUID id : pairedShotTurn.team().getTeamMembers()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.getWorld() == marker.getWorld())
                    player.spawnParticle(Particle.DUST, marker, 6, .16, .35, .16, 0, new Particle.DustOptions(color, 1));
            }
        }
    }

    private void commitPairedLie(PairedShotTurn.Attempt selected, String selection) {
        Team team = pairedShotTurn.team();
        pairedShotTurn = null;
        ScoreCard card = round.getScoreCardByTeam(team);
        card.trackStroke();
        if (selected.outcome() == Outcome.HAZARD) {
            chargeObPenalty(card, selected.player());
            message(format == RoundFormat.WORST_SHOT
                    ? "§cWorst Shot used the hazard. One penalty stroke. Replay the previous lie."
                    : "§cBest Shot: no legal lie. One penalty stroke. Replay the previous lie.");
        } else {
            String result = selection == null ? (format == RoundFormat.WORST_SHOT ? "Worst Shot" : "Best Shot")
                    + " used " + selected.name() + "'s lie"
                    : selection + " (" + selected.name() + "'s throw)";
            message(ChatColor.GREEN + result + ".");
        }
        Location lie = selected.lie();
        lies.put(team, lie);
        RoundPresentation.teleportTeamToLie(team, round, lie);
        if (card.getCurrentStrokes() >= maxStrokes()) finishHole(team, "Stroke limit");
        round.nextTurn();
    }

    private void showDecal(Player player, Location basket) {
        var data = round.getPlayerDataManager().getDataByPlayer(player.getUniqueId());
        if (data == null) return;
        ScoreDecal decal = data.getDecalLocker().equipped();
        if (decal == ScoreDecal.NONE) return;
        try {
            decal.show(basket.clone().add(0, 1.45, 0));
        } catch (RuntimeException failure) {
            plugin().getLogger().log(java.util.logging.Level.WARNING, "Could not render basket decal", failure);
        }
    }

    private void awardPractice(Player player, DiscType disc, String technique, boolean hazard, boolean goal, Stance stance,
                               PracticeContext context) {
        var data = NDGManager.getInstance().getPlayerDataManager().getOrCreatePlayerData(player.getUniqueId());
        var gains = data.getSkills().practice(disc, technique, hazard, goal, stance, context);
        if (gains.isEmpty()) return;
        var summary = new StringJoiner(", ");
        for (var gain : gains) {
            summary.add(gain.skill() + " +" + gain.xp());
            if (gain.leveledUp()) {
                player.sendMessage(ChatColor.GREEN + "Level up! " + gain.skill() + " is now level " + gain.level() + ".");
            }
        }
        player.sendMessage(ChatColor.GRAY + "Skill XP: " + summary);
    }
    public int maxStrokes() {
        return round instanceof CompetitionRound competition ? competition.strokeLimit()
                : Math.max(3, plugin().getConfig().getInt("max-strokes-per-hole", 12));
    }
    private void finishHole(Team team, String reason) {
        if (round instanceof SingleThrowRound single) {
            for (UUID id : team.getTeamMembers()) plugin().cancelMeter(id);
            single.miss(team, reason);
            return;
        }
        ScoreCard card = round.getScoreCardByTeam(team);
        if (card == null || card.getHoleScores().containsKey(hole)) return;
        if (rouletteDraw != null && team.contains(rouletteDraw.player())) clearRouletteDraw();
        if (pairedShotTurn != null && pairedShotTurn.team() == team) pairedShotTurn = null;
        if (round instanceof MatchPlayRound match) {
            match.awardMatchHole(team, reason);
            for (Team side : round.getTeams()) {
                round.getScoreCardByTeam(side).resetCurrentStrokes();
                for (UUID id : side.getTeamMembers()) plugin().cancelMeter(id);
                round.setTeamScored(hole, side);
            }
            return;
        }
        card.recordHoleScore(hole, maxStrokes(), round.getCourse().holePars().get(hole));
        if (round instanceof DailyRound daily) daily.ghosts().announceHole(team.getOwner(), card, hole);
        card.resetCurrentStrokes();
        for (UUID id : team.getTeamMembers()) plugin().cancelMeter(id);
        round.setTeamScored(hole, team);
        message("§e" + reason + ": hole recorded at the stroke limit.");
    }
    public void concede(Player player) {
        if (spectating.replaying()) { player.sendMessage(ChatColor.RED + "Wait for the instant replay to finish."); return; }
        if (basketCelebration != null) { player.sendMessage("§cWait for the basket celebration."); return; }
        if (shot != null || RoundPresentation.isPreviewing(round)) { player.sendMessage("§cWait for the shot or preview to finish."); return; }
        Team team = team(player.getUniqueId());
        if (team == null || !round.isTurn(team)) { player.sendMessage("§cConcede on your team's turn."); return; }
        finishHole(team, "Hole conceded");
        round.nextTurn();
    }
    public void leave(UUID id) {
        Team team = team(id);
        if (team == null) return;
        if (round instanceof MatchPlayRound match) {
            if (!round.isOver()) {
                // A basket celebration can delay the next turn after both scores have already been committed.
                if (round.getTeams().stream().allMatch(side -> round.getScoreCardByTeam(side).getHoleScores().containsKey(hole)))
                    match.finishMatchHole();
                match.score().forfeit(id);
                NDGManager.getInstance().getRoundHandler().endRound(round);
            }
            return;
        }
        EliminationRound elimination = round instanceof EliminationRound mode ? mode : null;
        if (elimination != null) elimination.forfeit(id);
        if (round instanceof WeeklyTournamentRound weekly) weekly.retainCompletedCard(id);
        Team current = currentTeam();
        boolean interrupted = shot != null && shot.player().equals(id);
        removeParticipant(team, id);
        if (round.getTeams().isEmpty()) {
            var handler = NDGManager.getInstance().getRoundHandler();
            if (round instanceof WeeklyTournamentRound weekly && weekly.hasCompletedDepartures()) handler.endRound(round);
            else handler.cancelRound(round);
        } else if (elimination != null && round.getTeams().size() == 1) {
            var handler = NDGManager.getInstance().getRoundHandler();
            if (Bukkit.getPlayer(round.getTeams().getFirst().getOwner()) == null) handler.cancelRound(round);
            else handler.endRound(round);
        } else if (basketCelebration != null || spectating.replaying()) {
            // The scheduled turn change will use the remaining teams.
            return;
        } else if (usesPairedAttempts() && current == team && !team.getTeamMembers().isEmpty()) {
            if (shot == null) {
                if (pairedShotTurn == null) turnChanged();
                else continuePairedShotTurn(null);
            }
        } else if ((current == team || interrupted) && shot == null) {
            round.nextTurn();
        } else {
            ((normalmanv2.normalDiscGolf.common.round.DefaultRoundTurnManager) round.getRoundTurnManager()).reconcile();
        }
    }

    void removeEliminated(Team team) { removeParticipant(team, team.getOwner()); }

    private void removeParticipant(Team team, UUID id) {
        plugin().cancelMeter(id);
        courseEvents.removePlayer(id);
        spectating.removePlayer(id);
        if (round instanceof DailyRound daily) daily.ghosts().remove(id);
        roundBags.remove(id);
        lockedDiscs.remove(id);
        if (rouletteDisc(id) != null) clearRouletteDraw();
        shotSetups.remove(id);
        hud.remove(id);
        if (shot != null && shot.player().equals(id)) {
            spectating.abortFlight();
            DiscFlightManager.cleanupPlayer(id);
            shot = null;
        }
        team.removePlayer(id);
        if (team.getTeamMembers().isEmpty()) {
            if (pairedShotTurn != null && pairedShotTurn.team() == team) pairedShotTurn = null;
            round.removeTeam(team);
            round.getRoundScoreCardManager().removeScoreCard(team);
            lies.remove(team);
            lastThrower.remove(team);
        }
    }
    public void tick() {
        if (round.isOver()) return;
        for (Team team : new ArrayList<>(round.getTeams())) for (UUID id : new ArrayList<>(team.getTeamMembers()))
            if (Bukkit.getPlayer(id) == null) leave(id);
        if (round.isOver()) return;
        if (hole != round.getCurrentHole()) startHole();
        courseEvents.tick();
        if (RoundPresentation.isPreviewing(round) || spectating.replaying()) turnChangedSilently();
        if (shot == null && basketCelebration == null && !spectating.replaying() && System.currentTimeMillis() >= deadline && currentTeam() != null) {
            if (choosingLie() != null)
                commitPairedLie(pairedShotTurn.fallback(choiceBasket()), "Choice timed out. Best Shot selected the closer lie");
            else {
                finishHole(currentTeam(), "Turn timed out");
                round.nextTurn();
            }
            if (round.isOver()) return;
        }
        if (choosingLie() != null) showLieMarkers();
        if (round instanceof DailyRound daily) daily.ghosts().tick();
        hud.update(Math.max(0, (deadline - System.currentTimeMillis()) / 1000));
    }
    private void turnChangedSilently() {
        deadline = System.currentTimeMillis() + Math.max(10, plugin().getConfig().getInt("turn-timeout-seconds", 90)) * 1000L;
    }
    public void message(String text) {
        for (Team team : round.getTeams()) for (UUID id : team.getTeamMembers()) {
            Player player = Bukkit.getPlayer(id); if (player != null) player.sendMessage(text);
        }
    }
    public void dispose() {
        customMarkers.clear();
        spectating.dispose();
        if (round instanceof DailyRound daily) daily.ghosts().clear();
        courseEvents.dispose();
        if (basketCelebration != null) {
            basketCelebration.cancel();
            basketCelebration = null;
        }
        for (Team team : round.getTeams()) for (UUID id : team.getTeamMembers()) plugin().cancelMeter(id);
        shot = null;
        pairedShotTurn = null;
        lies.clear();
        lastThrower.clear();
        roundBags.clear();
        lockedDiscs.clear();
        rouletteDraw = null;
        shotSetups.clear();
        analytics.clear();
        hud.dispose();
    }
}
