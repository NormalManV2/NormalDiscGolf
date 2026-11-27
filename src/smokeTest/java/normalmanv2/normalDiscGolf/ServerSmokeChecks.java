package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.settings.RoundType;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.custom.CustomCourse;
import normalmanv2.normalDiscGolf.impl.course.event.CourseEvent;
import normalmanv2.normalDiscGolf.impl.course.event.RoundCourseEvents;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGenerationSettings;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseTerrain;
import normalmanv2.normalDiscGolf.impl.course.grid.HoleArchetype;
import normalmanv2.normalDiscGolf.impl.course.obstacle.generator.ObstacleGenerator;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.disc.util.MathUtil;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.disc.DiscStock;
import normalmanv2.normalDiscGolf.impl.disc.DiscTrajectory;
import normalmanv2.normalDiscGolf.impl.disc.ArcadeDisc;
import normalmanv2.normalDiscGolf.impl.disc.ArcadeFlight;
import normalmanv2.normalDiscGolf.impl.round.ArcadeRound;
import normalmanv2.normalDiscGolf.impl.disc.GroundPhysics;
import normalmanv2.normalDiscGolf.impl.disc.GroundPhysics.Impact;
import normalmanv2.normalDiscGolf.impl.disc.GroundPhysics.Motion;
import normalmanv2.normalDiscGolf.impl.disc.ShotSetup.Stance;
import normalmanv2.normalDiscGolf.impl.cosmetic.ScoreDecal;
import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.api.round.lifecycle.RoundResult;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import normalmanv2.normalDiscGolf.impl.round.BestShotRound;
import normalmanv2.normalDiscGolf.impl.round.WorstShotRound;
import normalmanv2.normalDiscGolf.impl.round.OneDiscRound;
import normalmanv2.normalDiscGolf.impl.round.EliminationRound;
import normalmanv2.normalDiscGolf.impl.round.ClosestToPinRound;
import normalmanv2.normalDiscGolf.impl.round.RoundFormat;
import org.bukkit.*;
import org.bukkit.block.data.type.Light;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import java.util.*;
import java.nio.file.*;
import java.util.zip.*;

public final class ServerSmokeChecks extends JavaPlugin {
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { verify(); getLogger().info("NDG_SMOKE_PASS"); }
            catch(Throwable failure) { getLogger().log(java.util.logging.Level.SEVERE,"NDG_SMOKE_FAIL",failure); }
            finally { Bukkit.shutdown(); }
        }, 40);
    }
    private void verify() throws Exception {
        var manager=NDGManager.getInstance();
        String name="ndg_smoke_"+UUID.randomUUID();
        var grid=new CourseGrid(23,9,List.of(TileTypes.values()),3,2026);
        var course=new CourseImpl(Division.JUNIOR,grid,name);
        var settings=new DefaultRoundSettings(8,RoundType.RECREATIONAL,Division.JUNIOR,false);
        var round=(RoundImpl)manager.getRoundHandler().createRound("ffa",course,name,settings);
        World world=Bukkit.getWorld(name);
        check(world!=null,"world created");
        new ObstacleGenerator(grid,Division.JUNIOR).generateObstacles(world);
        Location tee=course.tee(world,0);
        check(world.getBlockAt(tee.clone().subtract(0,1,0)).getType()==Material.SMOOTH_STONE,"tee anchor matches rendered tee");
        Location basket=course.basket(world,0);
        for (ScoreDecal decal : ScoreDecal.values()) decal.show(basket.clone().add(0, 1.45, 0));
        var goal=MathUtil.trace(round,basket.clone().add(-3,0,0),new Vector(6,0,0));
        check(goal!=null && goal.goal(),"current basket scores along swept segment");
        var other=course.basket(world,1);
        var wrong=MathUtil.trace(round,other.clone().add(-3,0,0),new Vector(6,0,0));
        check(wrong==null || !wrong.goal(),"other basket does not score");
        Location start=tee.clone().add(0,1,0);
        world.getBlockAt(start.clone().add(2,0,0)).setType(Material.OAK_SLAB);
        var slab=MathUtil.trace(round,start,new Vector(4,0,0));
        check(slab!=null && !slab.goal() && slab.location().getX()<start.getX()+3,"non-basket slab blocks shot without scoring");
        world.getBlockAt(start.clone().add(2,0,0)).setType(Material.AIR);
        var water = world.getBlockAt(start.clone().add(2,0,0));
        water.setType(Material.WATER, false);
        var splash = MathUtil.trace(round, start, new Vector(4,0,0));
        check(splash != null && splash.hazard() && splash.location().getBlock().getType() == Material.WATER,
                "passable water stops the disc");
        water.setType(Material.AIR, false);
        checkGroundMotion(round, world, tee);
        checkArcadeRules(round, world);
        checkCourseEvents(round, world);
        checkCustomCourse();
        var queue=manager.getRoundQueueManager();
        queue.addRoundToQueue(round);
        var commit=RoundQueueManager.class.getDeclaredMethod("commitParty",GameRound.class,List.class,String.class); commit.setAccessible(true);
        List<UUID> players=List.of(UUID.randomUUID(),UUID.randomUUID());
        check(commit.invoke(queue,round,players,"ffa")==RoundQueueManager.QueueResult.SUCCESS,"party commit");
        for(UUID player:players) check(queue.isPlayerQueued(player)&&round.containsPlayer(player),"party remains in queue after successful commit");
        check(round.getTeams().size()==2,"FFA party members have independent scorecards");
        var doubles=(RoundImpl)manager.getRoundHandler().createRound("doubles",course,"ndg_smoke_"+UUID.randomUUID(),new DefaultRoundSettings(4,RoundType.RECREATIONAL,Division.JUNIOR,false));
        queue.addRoundToQueue(doubles);
        check(commit.invoke(queue,doubles,players,"doubles")==RoundQueueManager.QueueResult.SUCCESS,"doubles party commit");
        check(doubles.getTeams().size()==1 && doubles.getTeams().getFirst().isFull(),"doubles party stays on one team");
        var startRound=RoundQueueManager.class.getDeclaredMethod("shouldStartRound",GameRound.class);startRound.setAccessible(true);
        check(!(boolean)startRound.invoke(queue,doubles),"one doubles team waits for opposition");
        commit.invoke(queue,doubles,List.of(UUID.randomUUID(),UUID.randomUUID()),"doubles");
        var four = List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        check(commit.invoke(queue,doubles,four,"doubles")==RoundQueueManager.QueueResult.SUCCESS,"four friends queue as two doubles teams");
        check(doubles.getTeams().size()==4 && doubles.getTeams().stream().allMatch(t->t.isFull()),"all doubles teams complete");
        check((boolean)startRound.invoke(queue,doubles),"two complete doubles teams can start");
        checkRules(round, world, false);
        checkRules(doubles, Bukkit.getWorld(doubles.getId()), true);
        checkBestShotRules(doubles);
        checkWorstShotRules(doubles);
        checkOneDiscRules(round);
        checkEliminationCourseWrap(round);
        checkClosestToPinLanding(round, world);
        checkThemedWorlds(manager);
        checkWoodlandWorld(manager);
        checkDesertAndSkyWorlds(manager);
        finishRound(round,world);
        verifyRoundRewards(round, players);
        var limitedTeam=doubles.play().currentTeam();
        var limitedCard=doubles.getScoreCardByTeam(limitedTeam);
        limitedCard.addPenalty(doubles.play().maxStrokes()+5);
        var limit=doubles.play().getClass().getDeclaredMethod("finishHole",normalmanv2.normalDiscGolf.api.team.Team.class,String.class);
        limit.setAccessible(true);limit.invoke(doubles.play(),limitedTeam,"Test timeout");
        check(limitedCard.getHoleScores().get(0)==doubles.play().maxStrokes(),"stroke limit caps penalties and concessions");
        check(limitedCard.getHoledOutCount() == 0, "a stroke-limit score earns no basket credit");
        manager.getRoundHandler().discardRound(doubles);queue.removeRoundFromQueue(doubles);
        queue.removeRoundFromQueue(round);manager.getRoundHandler().discardRound(round);
        check(Bukkit.getWorld(name)==null,"discard unloads world");
        check(!Files.exists(Bukkit.getWorldContainer().toPath().resolve(name)),"discard deletes owned world");
        Path zip=manager.getPacked().getBytes().length>0?NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getDataFolder().toPath().resolve("NormalDiscGolfPack.zip"):null;
        check(zip!=null && manager.getPacked().getHash().length==20,"resource pack SHA-1");
        check(Arrays.equals(java.security.MessageDigest.getInstance("SHA-1").digest(manager.getPacked().getBytes()),manager.getPacked().getHash()),"resource pack hash matches bytes");
        var delivered=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(manager.getPacked().getPackUrl())).build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        check(delivered.statusCode()==200 && Arrays.equals(delivered.body(),manager.getPacked().getBytes()),"HTTP resource pack delivery");
        var head = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest
                .newBuilder(java.net.URI.create(manager.getPacked().getPackUrl()))
                .method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody()).build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        check(head.statusCode() == 200 && head.body().length == 0
                && head.headers().firstValueAsLong("Content-Length").orElse(-1) == manager.getPacked().getBytes().length,
                "pack metadata is available without downloading the ZIP");
        byte[] hash = manager.getPacked().getHash().clone();
        manager.getPacked().savePack();
        check(Arrays.equals(hash, manager.getPacked().getHash()), "unchanged pack bytes keep the same hash");
        verifySkillPersistence();
        try(ZipFile pack=new ZipFile(zip.toFile())) {
            for(String type:List.of("putter","midrange","driver")) check(pack.getEntry("assets/ndg/items/"+type+".json")!=null,"pack contains "+type);
            for (DiscModel model : DiscModel.builtIns()) {
                check(pack.getEntry("assets/ndg/items/" + model.id() + ".json") != null, "model listed: " + model.id());
                for (String part : List.of("plate", "rim")) {
                    var texture = pack.getEntry("assets/ndg/textures/item/" + model.id() + "_" + part + ".png");
                    check(texture != null, "pack contains texture: " + model.id());
                    try (var input = pack.getInputStream(texture)) {
                        var image = javax.imageio.ImageIO.read(input);
                        check(image != null && image.getWidth() == 16 && image.getHeight() == 16, "valid disc texture");
                    }
                }
            }
            verifyCustomDiscs(pack);
            for (var disc : ArcadeDisc.all()) {
                String id = disc.model().id();
                check(pack.getEntry("assets/ndg/items/" + id + ".json") != null
                        && pack.getEntry("assets/ndg/textures/item/" + id + "_plate.png") != null,
                        "arcade resource pack includes " + id);
                check(manager.getDiscCatalog().find(id) == null, "arcade loaners stay out of the shop catalog");
            }
            for(var entries=pack.entries();entries.hasMoreElements();) {
                var entry=entries.nextElement();
                if (entry.getName().endsWith(".png")) continue;
                try(var reader=new java.io.InputStreamReader(pack.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)) {
                    check(com.google.gson.JsonParser.parseReader(reader).isJsonObject(),"valid JSON: "+entry.getName());
                }
            }
        }
    }

    private void checkGroundMotion(RoundImpl round, World world, Location tee) {
        int x = tee.getBlockX(), z = tee.getBlockZ(), y = 120;
        Material[] lanes = {Material.SMOOTH_STONE, Material.GRASS_BLOCK, Material.SAND,
                Material.GRASS_BLOCK, Material.GRASS_BLOCK};
        for (int lane = 0; lane < lanes.length; lane++)
            for (int dx = 0; dx < 16; dx++) world.getBlockAt(x + dx, y - 1, z + lane).setType(lanes[lane], false);
        world.getBlockAt(x + 3, y, z + 3).setType(Material.SPRUCE_LOG, false);
        world.getBlockAt(x + 3, y, z + 4).setType(Material.SPRUCE_LEAVES, false);

        Location start = new Location(world, x + .5, y + .25, z + .5);
        DiscTrajectory skip = testTrajectory(round, start, new Vector(1.6, -.28, 0), 0);
        var bounce = skip.advance();
        check(bounce.impact() == Impact.SKIP && !bounce.finished(), "a shallow stone hit skips");
        check(skip.advance().location().getY() > bounce.location().getY(), "a skip lifts off the solid face");

        Vector steep = new Vector(1.45, -.6, 0);
        Location grass = start.clone().add(0, 0, 1);
        DiscTrajectory slide = testTrajectory(round, grass, steep, 0);
        check(slide.advance().impact() == Impact.SLIDE, "grass slides on a steep landing");
        double slideEnd = finishTrajectory(slide).location().getX();
        DiscTrajectory sand = testTrajectory(round, start.clone().add(0, 0, 2), steep, 0);
        check(sand.advance().impact() == Impact.SLIDE, "sand absorbs the landing without a skip");
        check(finishTrajectory(sand).location().getX() < slideEnd, "sand stops sooner than grass");

        DiscTrajectory roll = testTrajectory(round, grass, steep, 1);
        check(roll.advance().impact() == Impact.ROLL, "roller stance starts ground travel");
        check(finishTrajectory(roll).location().getX() > slideEnd + 3, "a roller travels farther over grass");
        world.getBlockAt(x + 5, y, z + 1).setType(Material.WATER, false);
        check(finishTrajectory(testTrajectory(round, grass, steep, 1)).hazard(), "a rolling disc stops at water");

        DiscTrajectory tree = testTrajectory(round, start.clone().add(0, .25, 3), new Vector(1.4, 0, 0), 0);
        tree.advance();
        var kick = tree.advance();
        check(kick.impact() == Impact.TREE_KICK && !kick.finished(), "a trunk deflects a fast throw");
        check(tree.advance().location().getX() < kick.location().getX(), "the kick moves away from the trunk");
        DiscTrajectory leaves = testTrajectory(round, start.clone().add(0, .25, 4), new Vector(1.4, 0, 0), 0);
        leaves.advance();
        var caught = leaves.advance();
        check(caught.finished() && caught.impact() == Impact.NONE, "leaves catch the disc");

        Location basket = round.getCourse().basket(world, round.getCurrentHole());
        int deck = (int) Math.ceil(basket.getY() - 1);
        // Use the open diagonal between the basket's decorative arms.
        for (int offset = -6; offset < 0; offset++) {
            int laneX = basket.getBlockX() + offset, laneZ = basket.getBlockZ() + offset;
            world.getBlockAt(laneX, deck - 1, laneZ).setType(Material.GRASS_BLOCK, false);
            world.getBlockAt(laneX, deck, laneZ).setType(Material.AIR, false);
            world.getBlockAt(laneX, deck + 1, laneZ).setType(Material.AIR, false);
        }
        Location approach = new Location(world, basket.getX() - 6, deck + .25, basket.getZ() - 6);
        double diagonalSpeed = steep.getX() / Math.sqrt(2);
        DiscTrajectory basketRoll = testTrajectory(round, approach, new Vector(diagonalSpeed, steep.getY(), diagonalSpeed), 1);
        check(basketRoll.advance().impact() == Impact.ROLL, "basket approach starts rolling");
        var score = finishTrajectory(basketRoll);
        check(score.goal(), "a rollout can score at the current basket, ended at " + score.location().toVector());
    }

    private static DiscTrajectory testTrajectory(RoundImpl round, Location origin, Vector velocity, int rollerLevel) {
        return new DiscTrajectory(origin, velocity, 40, (from, movement) -> MathUtil.trace(round, from, movement),
                (movement, tick) -> {}, new GroundPhysics(false, rollerLevel, 0));
    }

    private static DiscTrajectory.Step finishTrajectory(DiscTrajectory trajectory) {
        DiscTrajectory.Step step;
        do { step = trajectory.advance(); } while (!step.finished());
        return step;
    }

    private void verifySkillPersistence() throws Exception {
        var plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
        var catalog = NDGManager.getInstance().getDiscCatalog();
        var store = new normalmanv2.normalDiscGolf.impl.player.profile.PlayerProfileStore(plugin, catalog);
        var data = new normalmanv2.normalDiscGolf.impl.player.PlayerData();
        UUID id = UUID.randomUUID();
        try {
            var legacy = new org.bukkit.configuration.file.YamlConfiguration();
            legacy.set("schema-version", 1);
            legacy.set("skills.form.level", 2);
            legacy.set("rating.rating", 884);
            legacy.set("rating.division", "JUNIOR");
            legacy.set("disc-shop.unlocked", List.of("float", "missing-custom-disc"));
            legacy.set("disc-shop.loadout.PUTTER", "float");
            legacy.set("score-decals.equipped", "retired-test-decal");
            legacy.save(plugin.getDataFolder().toPath().resolve("player-profiles").resolve(id + ".yml").toFile());
            var migrated = store.load(id);
            check(migrated.getUnavailableDiscIds().contains("missing-custom-disc"), "missing custom IDs survive load");
            check(migrated.getRating().getRating() == 884 && migrated.getRating().getPeakRating() == 884
                    && migrated.getRating().getCareerDivision().name().equals("MA2"),
                    "old ratings keep their numeric value and use the new division thresholds");
            check(migrated.getSkills().getForm().getLevel() == 2
                    && migrated.getDiscBag().capacity() == 5
                    && migrated.getDiscBag().packed().equals(List.of(DiscModel.FLOAT, DiscModel.STARTER_MIDRANGE, DiscModel.STARTER_DRIVER))
                    && migrated.getDecalLocker().equipped() == ScoreDecal.NONE,
                    "old profiles keep skills and receive the starter bag");
            for (int i = 0; i < 14; i++)
                data.getSkills().practice(DiscType.DRIVER, "backhand", false, false);
            for (Stance stance : List.of(Stance.FLEX, Stance.TOMAHAWK, Stance.THUMBER))
                data.getSkills().practice(DiscType.DRIVER, "forehand", false, false, stance);
            data.getSkills().asMap().get("putting").restoreProgress(1, 55);
            data.getSkills().practice(DiscType.PUTTER, "forehand", false, true, Stance.PUTT,
                    new normalmanv2.normalDiscGolf.impl.player.PlayerSkills.PracticeContext(true, true, true));
            data.getRating().setRating(950);
            data.getRating().recordRound(1, 1, 50);
            DiscModel custom = catalog.find("smoke-imported");
            data.setUnavailableDiscIds(migrated.getUnavailableDiscIds());
            data.setDiscBag(data.getDiscBag().earn(340 + custom.price()).unlock(DiscModel.FLOAT).pack(DiscModel.FLOAT)
                    .moveToFront(DiscModel.FLOAT).upgrade().spend(ScoreDecal.GOLD_RING.price())
                    .unlock(custom).pack(custom));
            data.setDecalLocker(data.getDecalLocker().unlock(ScoreDecal.GOLD_RING).equip(ScoreDecal.GOLD_RING));
            var rouletteCard = new normalmanv2.normalDiscGolf.impl.player.score.ScoreCard();
            rouletteCard.recordParticipation(id);
            rouletteCard.recordBasket(0, 2, 3, id, 15);
            data.setClubhouse(data.getClubhouse().recordRound(rouletteCard, id, RoundFormat.DISC_ROULETTE.id(),
                    1, java.time.LocalDate.of(2026, 1, 1)).progress());
            check(store.save(id, "SmokeSkillProfile", data), "persist skill gains");
            var loaded = store.reload(id);
            var restored = loaded.getSkills();
            check(restored.getBackhand().getLevel() == 2 && restored.getBackhand().getXp() == 5, "skill level and carried XP survive reload");
            check(restored.getFlex().getXp() == 5 && restored.getTomahawk().getXp() == 5 && restored.getThumber().getXp() == 5,
                    "all three trick skills survive a profile reload");
            check(restored.asMap().get("putting").getLevel() == 2 && restored.asMap().get("recovery").getXp() == 6
                    && restored.getWindReading().getXp() == 3 && restored.asMap().get("mental-focus").getXp() == 8,
                    "contextual skills persist levels and earned XP");
            check(loaded.getRating().getRating() == 927 && loaded.getRating().getPeakRating() == 950
                    && loaded.getRating().getLastRoundRating() == 792 && loaded.getRating().getCareerDivision().name().equals("MA1"),
                    "career rating, derived division and rating history survive a profile reload");
            check(loaded.getDiscBag().credits() == 40 && loaded.getDiscBag().owns(DiscModel.FLOAT)
                    && loaded.getDiscBag().capacity() == 7 && loaded.getDiscBag().packed().getFirst() == DiscModel.FLOAT
                    && loaded.getDiscBag().packed().contains(DiscModel.STARTER_PUTTER), "credits, bag capacity and ordered discs survive reload");
            check(loaded.getDecalLocker().owns(ScoreDecal.GOLD_RING)
                    && loaded.getDecalLocker().equipped() == ScoreDecal.GOLD_RING, "decal ownership and selection survive reload");
            check(loaded.getDiscBag().packed().contains(custom) && loaded.getDiscBag().owns(custom)
                    && loaded.getUnavailableDiscIds().contains("missing-custom-disc"), "packed and unavailable custom disc ownership survives reload");
            check(loaded.getClubhouse().bestCards().equals(Map.of("disc-roulette-1", -1)),
                    "Disc Roulette's separate clean scorecard survives reload");
        } finally {
            Files.deleteIfExists(plugin.getDataFolder().toPath().resolve("player-profiles").resolve(id + ".yml"));
        }
    }

    private void verifyCustomDiscs(ZipFile pack) throws Exception {
        var manager = NDGManager.getInstance();
        var catalog = manager.getDiscCatalog();
        DiscModel generated = catalog.find("smoke-generated");
        DiscModel imported = catalog.find("smoke-imported");
        DiscModel fallback = catalog.find("smoke-fallback");
        check(generated != null && imported != null && fallback != null, "all custom definitions loaded");
        check(catalog.assets(generated) == null && catalog.assets(imported) != null && catalog.assets(fallback) == null,
                "generated, imported and fallback models selected");
        check(pack.getEntry("assets/ndg/textures/item/smoke-generated_plate.png") != null
                && pack.getEntry("assets/ndg/textures/item/smoke-fallback_plate.png") != null, "generated appearance and fallback in pack");
        check(pack.getEntry("assets/ndg/textures/custom/smoke-imported/item/stamp.png") != null, "uploaded PNG in pack");
        try (var input = pack.getInputStream(pack.getEntry("assets/ndg/models/item/smoke-imported.json"))) {
            String model = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            check(model.contains("ndg:custom/smoke-imported/item/stamp") && model.contains("minecraft:item/generated"),
                    "model references imported texture and vanilla parent");
        }
        check(manager.getDiscRegistry().getDiscByName(imported.displayName()).getSpeed() == 6,
                "imported disc uses configured flight stats");
        var epoch = java.time.Instant.parse("2026-01-01T00:00:00Z");
        check(java.util.stream.IntStream.range(0, 6)
                        .anyMatch(day -> DiscStock.at(epoch.plusSeconds(day * 86400L), 24, catalog.models()).offers().contains(imported)),
                "custom disc participates in rotation");
    }

    private void verifyRoundRewards(RoundImpl round, List<UUID> ids) throws Exception {
        var plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
        var profiles = round.getPlayerDataManager();
        var lifecycle = round.getRoundLifecycle();
        // This fixture uses simulated players, so mark start after completing the simulated scorecards.
        ids.forEach(profiles::getOrCreatePlayerData);
        var started = lifecycle.getClass().getDeclaredField("started");
        started.setAccessible(true);
        started.setBoolean(lifecycle, true);
        var expected = new HashMap<UUID, normalmanv2.normalDiscGolf.impl.player.clubhouse.ClubhouseProgress.Result>();
        var day = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        int holes = round.getCourse().holes();
        var trained = new normalmanv2.normalDiscGolf.impl.player.PlayerSkills(1, 5);
        trained.getPower().restoreProgress(5, 0);
        check(round.play().effectiveSkills(trained).launchSpeedMultiplier() < trained.launchSpeedMultiplier(),
                "rated stroke play normalizes skill effects without editing player progress");
        for (UUID id : ids) {
            var card = round.getScoreCardByTeam(round.play().team(id));
            expected.put(id, profiles.getDataByPlayer(id).getClubhouse().recordRound(card, id, "ffa", holes, day));
        }
        try {
            check(lifecycle.end() == RoundResult.ENDED, "completed round applies the rewards");
            check(lifecycle.end() == RoundResult.INVALID, "a second finish cannot award credits");
            for (UUID id : ids) {
                profiles.reloadPlayerData(id);
                var profile = profiles.getDataByPlayer(id);
                check(profile.getDiscBag().credits() == holes * 10L + expected.get(id).bonusCredits(),
                        "basket and clubhouse rewards persisted exactly once per player");
                check(profile.getClubhouse().equals(expected.get(id).progress()) && profile.getClubhouse().aces() == holes,
                        "completed round saved personal aces, medals, and daily progress");
                check(profile.getRating().getTotalRounds() == 1 && profile.getRating().getLastRoundRating() > 900,
                        "a fully holed-out stroke scorecard updates career rating once");
            }
        } finally {
            for (UUID id : ids) {
                profiles.unregisterPlayerData(id);
                Files.deleteIfExists(plugin.getDataFolder().toPath().resolve("player-profiles").resolve(id + ".yml"));
            }
        }
    }
    private void checkRules(RoundImpl round, World world, boolean doubles) {
        round.setState(normalmanv2.normalDiscGolf.impl.round.RoundState.START);
        for(var team:round.getTeams()) round.getRoundScoreCardManager().addScoreCard(team,new normalmanv2.normalDiscGolf.impl.player.score.ScoreCard());
        round.selectRandomTeamTurn();
        round.play().startHole();
        var team=round.play().currentTeam();
        UUID selected=round.play().thrower(team);
        var location=new java.util.concurrent.atomic.AtomicReference<>(round.getCourse().tee(world,0));
        org.bukkit.entity.Player player=(org.bukkit.entity.Player)java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),new Class[]{org.bukkit.entity.Player.class},(proxy,method,args)-> {
            return switch(method.getName()) {
                case "getUniqueId" -> selected;
                case "getWorld" -> world;
                case "getLocation" -> location.get().clone();
                case "getName","getDisplayName" -> "SmokePlayer";
                case "isOnline" -> true;
                case "sendMessage" -> null;
                case "hashCode" -> selected.hashCode();
                case "equals" -> proxy==args[0];
                default -> method.getReturnType()==boolean.class?false:method.getReturnType()==int.class?0:null;
            };
        });
        check(round.play().validate(player)==null,"selected thrower can throw from lie");
        location.set(location.get().clone().add(10,0,0));
        check(round.play().validate(player)!=null,"walking away from lie rejects throw");
        location.set(round.getCourse().tee(world,0));
        check(round.play().beginShot(player),"shot accepted at lie");
        check(!round.play().beginShot(player),"overlapping shot rejected");
        round.getScoreCardByTeam(team).trackStroke();
        if(doubles) {
            var splash = new DiscTrajectory.Step(location.get().clone().add(1, 0, 0), true, false, true,
                    Motion.ROLL, Impact.NONE);
            round.play().resolve(player, splash, null, null, false);
            check(round.getScoreCardByTeam(team).getCurrentStrokes() == 2
                    && round.play().lie(team).equals(location.get()), "a rolling hazard adds one penalty and restores the lie");
            check(!selected.equals(round.play().thrower(team)),"doubles alternates thrower after resolution");
            round.play().resolve(player, splash, null, null, false);
            check(round.getScoreCardByTeam(team).getCurrentStrokes() == 2, "a resolved hazard cannot be penalized twice");
        } else {
            round.play().resolve(player,round.getCourse().basket(world,0),true);
            check(round.getScoreCardByTeam(team).getHoleScores().get(0)==1,"goal records one stroke");
            check(!round.isTurn(team),"scoring team yields turn");
            round.play().resolve(player,round.getCourse().basket(world,0),true);
            check(round.getScoreCardByTeam(team).getTotalStrokes()==1,"stale flight cannot score twice");
        }
    }

    private void checkBestShotRules(RoundImpl fixture) throws Exception {
        var plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
        World world = Bukkit.getWorld(fixture.getId());
        var best = new BestShotRound(fixture.getCourse(), fixture.getId(),
                new DefaultRoundSettings(4, RoundType.RECREATIONAL, Division.JUNIOR, false));
        best.wire(best, plugin);
        var queue = NDGManager.getInstance().getRoundQueueManager();
        var ready = RoundQueueManager.class.getDeclaredMethod("shouldStartRound", GameRound.class);
        ready.setAccessible(true);
        for (int i = 0; i < 2; i++) {
            var pair = new normalmanv2.normalDiscGolf.impl.team.TeamImpl(UUID.randomUUID(), 2);
            pair.addPlayer(UUID.randomUUID());
            best.addTeam(pair);
            best.getRoundScoreCardManager().addScoreCard(pair, new normalmanv2.normalDiscGolf.impl.player.score.ScoreCard());
            check((boolean) ready.invoke(queue, best) == (i == 1), "Best Shot waits for two full teams");
        }
        check(RoundFormat.of(best) == RoundFormat.BEST_SHOT, "Best Shot has a separate queue identity from alternate shot");
        best.setState(normalmanv2.normalDiscGolf.impl.round.RoundState.START);
        best.selectRandomTeamTurn();
        best.play().startHole();
        var firstTeam = best.play().currentTeam();
        var start = best.play().lie(firstTeam);
        var firstCard = best.getScoreCardByTeam(firstTeam);
        UUID first = best.play().thrower(firstTeam);
        playBestShotAttempt(best, world, start, true, false);
        check(firstCard.getCurrentStrokes() == 0 && best.isTurn(firstTeam)
                && !best.play().thrower(firstTeam).equals(first) && best.play().lie(firstTeam).equals(start),
                "first hazard holds the team turn and leaves the second player the same starting lie");
        playBestShotAttempt(best, world, start, false, false);
        check(firstCard.getCurrentStrokes() == 1 && !best.isTurn(firstTeam), "only the legal attempt is used without its partner's penalty");

        var secondTeam = best.play().currentTeam();
        var secondLie = best.play().lie(secondTeam);
        playBestShotAttempt(best, world, secondLie, true, false);
        playBestShotAttempt(best, world, secondLie, true, false);
        check(best.getScoreCardByTeam(secondTeam).getCurrentStrokes() == 2
                && best.play().lie(secondTeam).equals(secondLie), "two hazards replay the old lie and count a single penalty");

        Location previous = best.play().lie(firstTeam);
        Location chosen = previous.clone().add(2, 0, 0);
        chosen.setY(best.getCourse().grid().surfaceHeight(chosen.getBlockX(), chosen.getBlockZ()) + 1);
        playBestShotAttempt(best, world, previous, false, false);
        playBestShotAttempt(best, world, chosen, false, false);
        check(best.play().choosingLie() == firstTeam && firstCard.getCurrentStrokes() == 1
                && best.play().lie(firstTeam).equals(previous), "two legal throws wait without changing score or lie");
        var picker = bestShotPlayer(firstTeam.getOwner(), start);
        best.play().chooseLie(picker, 2, "expired");
        check(best.play().choosingLie() == firstTeam, "expired choice cannot change a later lie");
        best.play().chooseLie(picker, 2, null);
        check(firstCard.getCurrentStrokes() == 2 && best.play().choosingLie() == null
                && best.play().lie(firstTeam).distanceSquared(chosen) < .01, "the chosen second landing becomes the shared lie");
        best.play().chooseLie(picker, 1, null);
        check(firstCard.getCurrentStrokes() == 2, "the same selection cannot count twice");

        playBestShotAttempt(best, world, secondLie, false, false);
        playBestShotAttempt(best, world, fixture.getCourse().basket(world, 0), false, true);
        check(best.getScoreCardByTeam(secondTeam).getHoleScores().get(0) == 3, "the second throw's basket commits one team stroke");
        playBestShotAttempt(best, world, chosen, false, false);
        best.play().leave(best.play().thrower(firstTeam));
        check(best.play().choosingLie() == null && firstCard.getCurrentStrokes() == 3 && best.isTurn(firstTeam),
                "a teammate leaving before the second attempt commits the available lie");
        best.dispose();
    }

    private void checkWorstShotRules(RoundImpl fixture) {
        World world = Bukkit.getWorld(fixture.getId());
        var worst = new WorstShotRound(fixture.getCourse(), fixture.getId(),
                new DefaultRoundSettings(4, RoundType.RECREATIONAL, Division.JUNIOR, false));
        worst.wire(worst, NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class));
        for (int i = 0; i < 2; i++) {
            var pair = new normalmanv2.normalDiscGolf.impl.team.TeamImpl(UUID.randomUUID(), 2);
            pair.addPlayer(UUID.randomUUID());
            worst.addTeam(pair);
            worst.getRoundScoreCardManager().addScoreCard(pair, new normalmanv2.normalDiscGolf.impl.player.score.ScoreCard());
        }
        check(RoundFormat.of(worst) == RoundFormat.WORST_SHOT, "Worst Shot has a separate queue identity");
        worst.setState(normalmanv2.normalDiscGolf.impl.round.RoundState.START);
        worst.selectRandomTeamTurn();
        worst.play().startHole();
        var first = worst.play().currentTeam();
        var start = worst.play().lie(first);
        var basket = worst.getCourse().basket(world, 0);
        var firstCard = worst.getScoreCardByTeam(first);
        playBestShotAttempt(worst, world, basket, false, true);
        check(worst.isTurn(first) && firstCard.getCurrentStrokes() == 0 && firstCard.getHoledOutCount() == 0,
                "one basket waits for the other attempt");
        playBestShotAttempt(worst, world, start, true, false);
        check(firstCard.getCurrentStrokes() == 2 && worst.play().lie(first).equals(start) && firstCard.getHoledOutCount() == 0,
                "a hazard takes precedence over a basket and adds one penalty");
        var second = worst.play().currentTeam();
        Location closer = start.clone().add(basket.toVector().subtract(start.toVector()).setY(0).normalize().multiply(2));
        playBestShotAttempt(worst, world, closer, false, false);
        playBestShotAttempt(worst, world, start, false, false);
        check(worst.getScoreCardByTeam(second).getCurrentStrokes() == 1 && worst.play().choosingLie() == null
                && worst.play().lie(second).distanceSquared(start) < .01, "the farther legal lie is committed automatically");
        playBestShotAttempt(worst, world, basket, false, true);
        playBestShotAttempt(worst, world, basket, false, true);
        check(firstCard.getHoleScores().get(0) == 3 && firstCard.getHoledOutCount() == 1,
                "two baskets finish the hole with one committed stroke");
        worst.dispose();
    }

    private void checkOneDiscRules(RoundImpl fixture) {
        var oneDisc = new OneDiscRound(fixture.getCourse(), fixture.getId(),
                new DefaultRoundSettings(8, RoundType.RECREATIONAL, Division.JUNIOR, false));
        oneDisc.wire(oneDisc, NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class));
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        for (UUID player : List.of(first, second)) {
            oneDisc.addTeam(new normalmanv2.normalDiscGolf.impl.team.TeamImpl(player, 1));
            oneDisc.play().setPackedDiscs(player, List.of(DiscModel.STARTER_DRIVER, DiscModel.TAILWIND, DiscModel.STARTER_PUTTER));
        }
        check(RoundFormat.of(oneDisc) == RoundFormat.ONE_DISC && RoundFormat.parse("ONE_DISC") == RoundFormat.ONE_DISC,
                "One Disc has a separate queue identity");
        check(!oneDisc.play().lockDisc(first, DiscModel.STARTER_DRIVER), "the choice requires an active round");
        oneDisc.setState(normalmanv2.normalDiscGolf.impl.round.RoundState.START);
        oneDisc.selectRandomTeamTurn();
        oneDisc.play().startHole();
        var team = oneDisc.play().currentTeam();
        var player = bestShotPlayer(team.getOwner(), oneDisc.play().lie(team));
        check(!oneDisc.play().beginShot(player) && oneDisc.play().throwableDiscs(first).isEmpty(),
                "an active player cannot throw before choosing a disc");
        check(!oneDisc.play().lockDisc(first, DiscModel.STARTER_MIDRANGE)
                && !oneDisc.play().lockDisc(UUID.randomUUID(), DiscModel.STARTER_DRIVER),
                "unpacked discs and nonparticipants cannot be locked");
        check(oneDisc.play().lockDisc(first, DiscModel.STARTER_DRIVER)
                && oneDisc.play().lockDisc(second, DiscModel.STARTER_PUTTER), "players lock their own discs independently");
        check(!oneDisc.play().lockDisc(first, DiscModel.TAILWIND)
                && !oneDisc.play().lockDisc(first, DiscModel.STARTER_PUTTER),
                "a second choice cannot overwrite the lock, even for a disc of the same type");
        check(oneDisc.play().beginShot(player), "choosing allows the normal shot flow");
        oneDisc.play().failedShot();
        oneDisc.getRoundTurnManager().advanceHole();
        oneDisc.play().startHole();
        check(oneDisc.play().throwableDiscs(first).equals(List.of(DiscModel.STARTER_DRIVER))
                && oneDisc.play().throwableDiscs(second).equals(List.of(DiscModel.STARTER_PUTTER))
                && oneDisc.play().packedDiscs(first).size() == 3, "hole changes keep both locks and the packed snapshot");
        oneDisc.dispose();
        check(oneDisc.play().lockedDisc(first) == null, "round disposal clears the lock");
    }

    private void checkEliminationCourseWrap(RoundImpl fixture) {
        var elimination = new EliminationRound(fixture.getCourse(), fixture.getId(),
                new DefaultRoundSettings(8, RoundType.RECREATIONAL, Division.JUNIOR, false));
        elimination.wire(elimination, NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class));
        var team = new normalmanv2.normalDiscGolf.impl.team.TeamImpl(UUID.randomUUID(), 1);
        elimination.addTeam(team);
        elimination.selectRandomTeamTurn();
        elimination.setTeamScored(0, team);
        check(!elimination.isTurn(team), "a completed turn is closed before a course wrap");
        for (int hole = 0; hole < elimination.getCourse().holes(); hole++)
            elimination.getRoundTurnManager().advanceHole();
        check(elimination.getCurrentHole() == 0 && elimination.isTurn(team),
                "Elimination returns to the first tee and opens fresh turns");
        elimination.dispose();
    }

    private void checkCustomCourse() {
        World world = Bukkit.getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();
        int x = spawn.getBlockX() + 12, z = spawn.getBlockZ() + 12, y = world.getHighestBlockYAt(x, z) + 8;
        var tee = new CourseGrid.GridPoint(x, y, z);
        var pin = new CourseGrid.GridPoint(x + 12, y, z);
        var supports = List.of(world.getBlockAt(x, y - 1, z), world.getBlockAt(x + 12, y - 1, z));
        var original = supports.stream().map(org.bukkit.block.Block::getBlockData).toList();
        var ceiling = world.getBlockAt(x + 12, y + 2, z);
        var oldCeiling = ceiling.getBlockData();
        try {
            supports.forEach(block -> block.setType(Material.STONE, false));
            var layout = Map.of(0, new CustomCourse.Hole(tee, pin, 4));
            var course = new CustomCourse("smoke-editor", "Editor Smoke", UUID.randomUUID(), world.getUID(), world.getName(), layout);
            check(course.validate().isEmpty() && course.holePars().get(0) == 4
                    && course.tee(world, 0).getY() == y, "custom anchors keep their real elevation and assigned par");
            var round = (RoundImpl)NDGManager.getInstance().getRoundHandler().createCustomRound(course,
                    new DefaultRoundSettings(8, RoundType.RECREATIONAL, Division.JUNIOR, true));
            check(round.world() == world && !round.getId().equals(world.getName()), "saved round uses its permanent world");
            var basket = course.basket(world, 0);
            check(MathUtil.trace(round, basket.clone().add(-3, 0, 0), new Vector(6, 0, 0)).goal(),
                    "custom basket scores through the normal flight collision path");
            check(course.safeLie(basket.clone().add(0, 2, 0), course.tee(world, 0)).getY() == y,
                    "custom lies locate the real floor below the disc");
            ceiling.setType(Material.STONE, false);
            check(course.validate().stream().anyMatch(error -> error.contains("third clear block")),
                    "custom validation catches a blocked basket");
            NDGManager.getInstance().getRoundHandler().discardRound(round);
            check(Bukkit.getWorld(world.getUID()) == world, "discarding a custom round leaves its source world loaded");
        } finally {
            ceiling.setBlockData(oldCeiling, false);
            for (int index = 0; index < supports.size(); index++) supports.get(index).setBlockData(original.get(index), false);
        }
    }

    private void checkCourseEvents(RoundImpl round, World world) {
        var config = new MemoryConfiguration();
        config.set("course-events.include-rated", true);
        config.set("course-events.sequence", List.of("thunderstorm", "night-golf", "clear"));
        config.set("course-events.thunderstorm.lightning-flashes", false);
        config.set("course-events.thunderstorm.wind-scale", 1.5);
        var events = new RoundCourseEvents(round);
        var teeLight = round.getCourse().tee(world, 1).add(0, 2.1, 0).getBlock();
        var basketLight = round.getCourse().basket(world, 1).add(0, 2.3, 0).getBlock();
        var originalTee = teeLight.getBlockData().getAsString();
        var originalBasket = basketLight.getBlockData().getAsString();
        try {
            var wind = events.startHole(config, world, 0);
            check(events.current() == CourseEvent.THUNDERSTORM && world.hasStorm() && world.isThundering()
                    && wind.strength() >= 6, "thunderstorm sets weather and applies configured wind scale");
            config.set("flight.wind", false);
            check(events.startHole(config, world, 0).strength() == 0, "wind master switch also applies to storms");
            events.startHole(config, world, 1);
            check(!world.hasStorm() && !world.isThundering() && world.getTime() == 18000, "night begins after storm clears");
            check(teeLight.getBlockData() instanceof Light tee && tee.getLevel() == 15
                    && basketLight.getBlockData() instanceof Light basket && basket.getLevel() == 15,
                    "tee and basket have full-strength light");
            var lightCenter = basketLight.getLocation().add(.5, .5, .5);
            check(world.rayTraceBlocks(lightCenter.clone().add(0, .49, 0), new Vector(0, -1, 0), .97,
                    FluidCollisionMode.NEVER, true) == null, "night lights do not collide with discs");
            events.startHole(config, world, 2);
            check(events.current() == CourseEvent.CLEAR && world.getTime() == 6000
                    && originalTee.equals(teeLight.getBlockData().getAsString())
                    && originalBasket.equals(basketLight.getBlockData().getAsString()),
                    "clear conditions remove temporary lighting and restore daylight");
            events.startHole(config, world, 1);
        } finally {
            events.dispose();
        }
        check(world.getTime() == 6000 && originalBasket.equals(basketLight.getBlockData().getAsString()),
                "disposing a night round removes its lights");
    }

    private void checkArcadeRules(RoundImpl fixture, World world) {
        var arcade = new ArcadeRound(fixture.getCourse(), fixture.getId(),
                new DefaultRoundSettings(8, RoundType.RECREATIONAL, Division.JUNIOR, false), 1);
        arcade.wire(arcade, NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class));
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        for (UUID id : List.of(first, second))
            arcade.addTeam(new normalmanv2.normalDiscGolf.impl.team.TeamImpl(id, 1));
        check(!arcade.spend(first, ArcadeDisc.PHANTOM), "a queued arcade round spends no charges");
        arcade.setState(normalmanv2.normalDiscGolf.impl.round.RoundState.START);
        check(arcade.spend(first, ArcadeDisc.PHANTOM) && !arcade.spend(first, ArcadeDisc.PHANTOM)
                && arcade.remaining(second, ArcadeDisc.PHANTOM) == 1, "charges are capped independently per player");
        arcade.refund(first, ArcadeDisc.PHANTOM);
        check(arcade.spend(first, ArcadeDisc.PHANTOM), "a failed launch can return its charge");
        arcade.getRoundTurnManager().advanceHole();
        check(arcade.remaining(first, ArcadeDisc.PHANTOM) == 0 && arcade.remaining(first, ArcadeDisc.ROCKET) == 1,
                "charges remain spent across hole transitions");
        arcade.dispose();

        Location from = fixture.getCourse().tee(world, 0).add(0, 1.6, 0);
        var firstBlock = from.clone().add(3, 0, 0).getBlock();
        var secondBlock = from.clone().add(7, 0, 0).getBlock();
        var firstData = firstBlock.getBlockData();
        var secondData = secondBlock.getBlockData();
        try {
            firstBlock.setType(Material.STONE, false);
            secondBlock.setType(Material.STONE, false);
            Vector sweep = new Vector(9, 0, 0);
            var normal = MathUtil.trace(fixture, from, sweep);
            check(normal != null && Math.abs(normal.location().getX() - firstBlock.getX()) < .1, "ordinary disc strikes the first block");
            var ghost = new ArcadeFlight(ArcadeDisc.PHANTOM);
            var hit = MathUtil.trace(fixture, from, sweep, ghost);
            check(firstBlock.equals(ghost.phasedBlock()) && hit != null && Math.abs(hit.location().getX() - secondBlock.getX()) < .1,
                    "phantom ignores one voxel and strikes the second block in the same sweep");
            var slowGhost = new ArcadeFlight(ArcadeDisc.PHANTOM);
            var entry = firstBlock.getLocation().add(-.2, .5, .5);
            check(MathUtil.trace(fixture, entry, new Vector(.45, 0, 0), slowGhost) == null && !slowGhost.canPhase(),
                    "phantom may end a tick inside the phased block");
            check(MathUtil.trace(fixture, entry.add(.45, 0, 0), new Vector(.3, 0, 0), slowGhost) == null,
                    "the same block remains passable across subsequent ticks");
        } finally {
            firstBlock.setBlockData(firstData, false);
            secondBlock.setBlockData(secondData, false);
        }
    }

    private void checkClosestToPinLanding(RoundImpl fixture, World world) {
        var race = new ClosestToPinRound(fixture.getCourse(), fixture.getId(),
                new DefaultRoundSettings(8, RoundType.RECREATIONAL, Division.JUNIOR, false));
        race.wire(race, NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class));
        for (int player = 0; player < 3; player++) {
            var team = new normalmanv2.normalDiscGolf.impl.team.TeamImpl(UUID.randomUUID(), 1);
            race.addTeam(team);
            race.getRoundScoreCardManager().addScoreCard(team, new normalmanv2.normalDiscGolf.impl.player.score.ScoreCard());
        }
        race.setState(normalmanv2.normalDiscGolf.impl.round.RoundState.START);
        race.selectRandomTeamTurn();
        race.play().startHole();
        var basket = race.getCourse().basket(world, 0);

        var first = race.play().currentTeam();
        var thrower = bestShotPlayer(first.getOwner(), race.play().lie(first));
        check(race.play().beginShot(thrower), "closest-to-pin allows the initial throw");
        race.getScoreCardByTeam(first).trackStroke();
        race.play().resolve(thrower, basket.clone().add(3, 1, 4), false);
        check(race.total(first).equals(new ClosestToPinRound.Total(0, 50)) && race.hasFinishedHole(first),
                "closest-to-pin scores the exact horizontal landing without snapping to a lie");
        check(!race.play().beginShot(thrower), "closest-to-pin rejects a second attempt");

        var second = race.play().currentTeam();
        var hazardThrower = bestShotPlayer(second.getOwner(), race.play().lie(second));
        check(race.play().beginShot(hazardThrower), "the next player receives an attempt");
        race.getScoreCardByTeam(second).trackStroke();
        var impact = new DiscTrajectory.Step(basket, true, false, true, Motion.AIR, Impact.NONE);
        race.play().resolve(hazardThrower, impact, null, null, Stance.NORMAL);
        check(race.total(second).equals(new ClosestToPinRound.Total(1, 0)) && race.hasFinishedHole(second),
                "a hazard records a fault and completes the attempt even beside the basket");
        race.dispose();
    }

    private void checkThemedWorlds(NDGManager manager) {
        var settings = new DefaultRoundSettings(8, RoundType.RECREATIONAL, Division.JUNIOR, false);
        for (CourseTerrain terrain : CourseTerrain.environments()) {
            var profile = new CourseGenerationSettings(terrain, 12, 15, .4, 1, List.of(HoleArchetype.AIR_SHOT));
            var grid = new CourseGrid(23, 9, List.of(TileTypes.values()), 1, 41L, CourseGrid.Layout.PARKLAND, profile);
            String name = "ndg_smoke_" + terrain.id() + "_" + UUID.randomUUID();
            var course = new CourseImpl(Division.JUNIOR, grid, name);
            var round = (RoundImpl)manager.getRoundHandler().createRound("ffa", course, name, settings, false);
            try {
                World world = Bukkit.getWorld(name);
                check(world != null && world.getSeed() == 41L, terrain + " world uses its configured seed");
                var tee = course.tee(world, 0);
                var base = tee.clone().subtract(0, 1, 0).getBlock();
                check(base.getType() == grid.terrainColumn(base.getX(), base.getZ()).surface()
                        && base.getType().isSolid() && tee.getBlock().isPassable(), terrain + " generates its standable surface");
                check(base.getBiome().getKey().getKey().equals(terrain.biome()), terrain + " generates the matching biome");
                var basket = course.basket(world, 0);
                check(basket.clone().subtract(0, 1.9, 0).getBlock().getType().isSolid(),
                        terrain + " provides solid basket footing");
                if (terrain == CourseTerrain.NETHER || terrain == CourseTerrain.ISLAND) {
                    Material liquid = terrain == CourseTerrain.NETHER ? Material.LAVA : Material.WATER;
                    Location hazard = findGeneratedColumn(grid, world, liquid);
                    check(hazard.getBlock().getType() == liquid, terrain + " generates its liquid hazard");
                    check(MathUtil.trace(round, hazard, new Vector(.2, 0, 0)).hazard(),
                            terrain + " detects a disc entering the liquid");
                }
                if (terrain == CourseTerrain.SKY)
                    check(tee.getY() > 115 && world.getBlockAt(0, 70, 0).isEmpty()
                            && grid.surfaceType(0, 0) == TileTypes.OUT_OF_BOUNDS, "Sky generates platforms over a real void");
                if (terrain == CourseTerrain.MOUNTAIN) check(tee.getY() > 90, "Mountain has an elevated course");
                getLogger().info("NDG_TERRAIN_PASS " + terrain.label());
            } finally {
                manager.getRoundHandler().discardRound(round);
            }
        }
    }

    private void checkWoodlandWorld(NDGManager manager) {
        var settings = new DefaultRoundSettings(8, RoundType.RECREATIONAL, Division.JUNIOR, false);
        var grid = new CourseGrid(23, 9, List.of(TileTypes.values()), 3, 2026, CourseGrid.Layout.PARKLAND,
                CourseGenerationSettings.defaults(CourseTerrain.WOODLAND));
        String name = "ndg_smoke_woodland_" + UUID.randomUUID();
        var course = new CourseImpl(Division.JUNIOR, grid, name);
        var round = manager.getRoundHandler().createRound("ffa", course, name, settings, false);
        try {
            World world = Bukkit.getWorld(name);
            check(world != null, "Woodland showcase world exists");
            grid.generateObstacles(world);
            for (int hole = 0; hole < 3; hole++) {
                var tee = course.tee(world, hole);
                check(tee.clone().add(2, -1, 0).getBlock().getType() == Material.STRIPPED_SPRUCE_WOOD,
                        "Woodland tee pad has its spruce border");
                var layout = grid.woodlandHole(hole);
                var sign = world.getNearbyEntities(tee, 8, 4, 8).stream().filter(TextDisplay.class::isInstance)
                        .map(TextDisplay.class::cast).filter(display -> display.getText().contains(layout.label()))
                        .findFirst().orElseThrow();
                check(sign.isPersistent() && sign.getText().contains("Par " + course.holePars().get(hole))
                                && sign.getText().contains("blocks") && sign.getText().contains(layout.hint(grid.centerline(hole))),
                        "the Woodland tee sign stays with its chunk and shows the current hole and shot tip");
                check(sign.getLocation().toVector().subtract(tee.toVector()).dot(tee.getDirection()) > 0,
                        "the tee sign is ahead of the player at the lie");
                for (var landmark : grid.woodlandLandmarks(hole)) {
                    int x = landmark.position().x(), z = landmark.position().z();
                    int y = grid.surfaceHeight(x, z) + 1;
                    Material cap = switch (landmark.type()) {
                        case OLD_GROWTH_PINE -> Material.SPRUCE_LEAVES;
                        case MOSSY_BOULDER -> Material.MOSS_BLOCK;
                        case RUINED_GATEPOST -> Material.LANTERN;
                    };
                    int top = y + landmark.height() + switch (landmark.type()) {
                        case OLD_GROWTH_PINE -> 0;
                        case MOSSY_BOULDER -> -1;
                        case RUINED_GATEPOST -> 1;
                    };
                    check(world.getBlockAt(x, top, z).getType() == cap, "placed Woodland " + landmark.type());
                }
            }
            getLogger().info("NDG_WOODLAND_PASS");
        } finally {
            manager.getRoundHandler().discardRound(round);
        }
    }

    private void checkDesertAndSkyWorlds(NDGManager manager) {
        var settings = new DefaultRoundSettings(8, RoundType.RECREATIONAL, Division.JUNIOR, false);
        for (var terrain : List.of(CourseTerrain.DESERT, CourseTerrain.SKY)) {
            var grid = new CourseGrid(23, 9, List.of(TileTypes.values()), 3, 2026, CourseGrid.Layout.PARKLAND,
                    CourseGenerationSettings.defaults(terrain));
            String name = "ndg_smoke_scenery_" + UUID.randomUUID();
            var round = manager.getRoundHandler().createRound("ffa", new CourseImpl(Division.JUNIOR, grid, name),
                    name, settings, false);
            try {
                World world = Bukkit.getWorld(name);
                check(world != null, "showcase world exists");
                if (terrain == CourseTerrain.DESERT) checkSandstoneArch(grid, world);
                else for (int hole = 0; hole < 3; hole++) checkSkySatellite(grid, world, hole);
            } finally {
                manager.getRoundHandler().discardRound(round);
            }
        }
        getLogger().info("NDG_DESERT_SKY_PASS");
    }

    private void checkSandstoneArch(CourseGrid grid, World world) {
        int hole = java.util.stream.IntStream.range(0, 3).filter(i -> grid.holeName(i).equals("Sandstone Gate"))
                .findFirst().orElseThrow();
        var tee = grid.getTeePoints().get(hole);
        var pin = grid.getHolePoints().get(hole);
        int dx = Integer.signum(pin.x() - tee.x()), dz = Integer.signum(pin.z() - tee.z());
        int length = Math.abs(pin.x() - tee.x()) + Math.abs(pin.z() - tee.z());
        var materials = Set.of(Material.SANDSTONE, Material.RED_SANDSTONE, Material.TERRACOTTA, Material.ORANGE_TERRACOTTA);
        int crownColumns = 0;
        for (int offset = length * 44 / 100; offset <= length * 65 / 100; offset++) {
            int x = tee.x() + dx * offset, z = tee.z() + dz * offset;
            int ground = grid.surfaceHeight(x, z);
            for (int y = ground + 1; y <= ground + 18; y++)
                check(world.getBlockAt(x, y, z).isEmpty(), "the sandstone arch clears the intended throw and camera line");
            for (int y = ground + 19; y <= ground + 45; y++) {
                if (!materials.contains(world.getBlockAt(x, y, z).getType())) continue;
                crownColumns++;
                break;
            }
        }
        check(crownColumns >= 4, "a generated sandstone crown spans the fairway opening");
    }

    private void checkSkySatellite(CourseGrid grid, World world, int hole) {
        for (int x = 16 + hole * 112; x < 16 + (hole + 1) * 112; x++) for (int z = 16; z < 128; z++) {
            var column = grid.terrainColumn(x, z);
            if (column.depth() == 0 || column.type() != TileTypes.OUT_OF_BOUNDS) continue;
            check(world.getBlockAt(x, column.height(), z).getType() == column.surface()
                            && world.getBlockAt(x, column.height() - column.depth() - 1, z).isEmpty(),
                    "Sky has a floating satellite island with open air below");
            check(grid.getTileTypeAtLocation(new Location(world, x, column.height() + 1, z)).isHazard(),
                    "the satellite is scenery and cannot become a legal lie");
            return;
        }
        throw new AssertionError("Sky showcase hole is missing background islands");
    }

    private Location findGeneratedColumn(CourseGrid grid, World world, Material material) {
        for (int x = 16; x < 128; x += 2) for (int z = 16; z < 128; z += 2) {
            var column = grid.terrainColumn(x, z);
            if (column.surface() == material) return new Location(world, x + .5, column.height() + .25, z + .5);
        }
        throw new AssertionError("Could not find generated " + material);
    }

    private void playBestShotAttempt(RoundImpl round, World world, Location landing, boolean hazard, boolean goal) {
        var play = round.play();
        var team = play.currentTeam();
        Location lie = play.lie(team);
        check(lie.getWorld() == world, "best shot remains in its course world");
        var player = bestShotPlayer(play.thrower(team), lie);
        check(play.beginShot(player), "best shot attempt starts from the recorded lie");
        var flight = new DiscTrajectory.Step(landing, true, goal, hazard, Motion.AIR, Impact.NONE);
        play.resolve(player, flight, null, null, false);
    }

    private org.bukkit.entity.Player bestShotPlayer(UUID id, Location lie) {
        return (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),
                new Class[]{org.bukkit.entity.Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getWorld" -> lie.getWorld();
                    case "getLocation" -> lie.clone();
                    case "getName", "getDisplayName" -> "BestShotProbe";
                    case "sendMessage" -> null;
                    case "isOnline" -> true;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
                });
    }

    private void finishRound(RoundImpl round, World world) {
        int limit=round.getCourse().holes()*round.getTeams().size()+2;
        while(!round.isOver() && limit-->0) {
            var team=round.play().currentTeam(); UUID id=round.play().thrower(team);
            Location lie=round.play().lie(team);
            org.bukkit.entity.Player player=(org.bukkit.entity.Player)java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),new Class[]{org.bukkit.entity.Player.class},(proxy,method,args)->switch(method.getName()) {
                case "getUniqueId" -> id;
                case "getWorld" -> world;
                case "getLocation" -> lie.clone();
                case "getName","getDisplayName" -> "SmokePlayer";
                case "isOnline" -> true;
                case "sendMessage" -> null;
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy==args[0];
                default -> method.getReturnType()==boolean.class?false:method.getReturnType()==int.class?0:null;
            });
            check(round.play().beginShot(player),"next hole accepts throw");
            round.getScoreCardByTeam(team).trackStroke();
            round.play().resolve(player,round.getCourse().basket(world,round.getCurrentHole()),true);
        }
        check(round.getState()==normalmanv2.normalDiscGolf.impl.round.RoundState.END,"final hole reaches END");
        for(var card:round.getScoreCards().values()) check(card.getHoleScores().size()==round.getCourse().holes(),"every team completed every hole");
    }

    private static void check(boolean condition,String name) { if(!condition) throw new AssertionError(name); }
}
