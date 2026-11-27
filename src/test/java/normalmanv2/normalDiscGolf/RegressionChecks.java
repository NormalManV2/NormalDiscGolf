package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.api.round.settings.RoundType;
import normalmanv2.normalDiscGolf.impl.competition.DailyCompetition;
import normalmanv2.normalDiscGolf.impl.competition.DailyCourse;
import normalmanv2.normalDiscGolf.impl.competition.DailyGhost;
import normalmanv2.normalDiscGolf.impl.competition.RankedSeason;
import normalmanv2.normalDiscGolf.impl.competition.RankedSeasonStore;
import normalmanv2.normalDiscGolf.impl.competition.StrokeStandings;
import normalmanv2.normalDiscGolf.impl.competition.WeeklyTournament;
import normalmanv2.normalDiscGolf.impl.competition.WeeklyTournamentStore;
import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGenerationSettings;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseTerrain;
import normalmanv2.normalDiscGolf.impl.course.grid.HoleArchetype;
import normalmanv2.normalDiscGolf.impl.course.grid.WoodlandHole;
import normalmanv2.normalDiscGolf.impl.course.event.CourseEvent;
import normalmanv2.normalDiscGolf.impl.course.event.CourseEventSettings;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.player.score.RoundAnalytics;
import normalmanv2.normalDiscGolf.impl.player.score.RoundSummary;
import normalmanv2.normalDiscGolf.impl.player.score.CareerDivision;
import normalmanv2.normalDiscGolf.impl.player.score.PDGARating;
import normalmanv2.normalDiscGolf.impl.player.clubhouse.ClubhouseMedal;
import normalmanv2.normalDiscGolf.impl.player.clubhouse.ClubhouseProgress;
import normalmanv2.normalDiscGolf.impl.player.clubhouse.DailyChallenge;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.player.DiscBag;
import normalmanv2.normalDiscGolf.impl.player.DecalLocker;
import normalmanv2.normalDiscGolf.impl.cosmetic.ScoreDecal;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.disc.Driver;
import normalmanv2.normalDiscGolf.impl.disc.DiscStock;
import normalmanv2.normalDiscGolf.impl.disc.DiscFlightEngine;
import normalmanv2.normalDiscGolf.impl.disc.DiscTrajectory;
import normalmanv2.normalDiscGolf.impl.disc.GroundPhysics;
import normalmanv2.normalDiscGolf.impl.disc.ArcadeDisc;
import normalmanv2.normalDiscGolf.impl.disc.ArcadeFlight;
import normalmanv2.normalDiscGolf.impl.disc.HoleWind;
import normalmanv2.normalDiscGolf.impl.disc.ShotSetup;
import normalmanv2.normalDiscGolf.impl.disc.ShotSetup.Stance;
import normalmanv2.normalDiscGolf.impl.disc.TrickFlight;
import normalmanv2.normalDiscGolf.impl.round.MatchPlayScore;
import normalmanv2.normalDiscGolf.impl.round.ClosestToPinRound;
import normalmanv2.normalDiscGolf.impl.round.RoundFormat;
import normalmanv2.normalDiscGolf.impl.round.DailyRound;
import normalmanv2.normalDiscGolf.impl.technique.ThrowTechnique;
import normalmanv2.normalDiscGolf.impl.technique.type.BackhandTechnique;
import normalmanv2.normalDiscGolf.impl.technique.type.ForehandTechnique;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.util.Vector;
import java.util.*;

/** Dependency-free regression suite; runs as part of Gradle check. */
public final class RegressionChecks {
    public static void main(String[] args) throws java.io.IOException {
        ScoreCard card = new ScoreCard();
        card.trackStroke(); card.undoStroke(); check(card.getCurrentStrokes()==0,"failed throw rolled back");
        card.trackStroke(); card.addPenalty(1); card.trackStroke();
        card.recordHoleScore(0,card.getCurrentStrokes(),3); card.resetCurrentStrokes();
        check(card.getTotalStrokes()==3 && card.getTotalScore()==0,"stroke and hazard totals");
        boolean rejected=false; try { card.recordHoleScore(0,3,3); } catch(IllegalStateException expected) { rejected=true; }
        check(rejected && card.getTotalStrokes()==3,"duplicate scoring rejected");
        for(int hole=1;hole<18;hole++) card.recordHoleScore(hole,3,3);
        check(card.getHoleScores().size()==18 && card.getTotalStrokes()==54,"full round scoring");
        checkClubhouse();
        checkShotSetup();
        checkStandardDriverFlight();
        checkTrickShots();
        checkMatchPlay();
        checkAceRaceCourses();
        checkThemedCourses();
        checkWoodlandLandmarks();
        checkClosestToPin();
        checkCareer();
        checkArcadeFlight();
        checkCourseEventSettings();
        checkRoundAnalytics();
        checkDailyCompetition();
        checkWeeklyTournament();
        checkRankedSeasons();
        check(CourseGrid.segmentDistance(5,3,0,0,10,0)==3,"lane distance perpendicular");
        check(CourseGrid.segmentDistance(-3,0,0,0,10,0)==3,"lane distance endpoint");
        var skills = new PlayerSkills(1, 5);
        double initialSpeed = skills.launchSpeedMultiplier(), initialSpread = skills.releaseSpreadDegrees();
        double initialDrag = skills.flightDrag();
        skills.practice(DiscType.PUTTER, "backhand", true, false);
        check(skills.getBackhand().getXp() == 1 && skills.getPower().getXp() == 0, "hazard practice");
        for (int i = 0; i < 24; i++) skills.practice(DiscType.DRIVER, "forehand", false, i % 3 == 0);
        check(skills.getBackhand().getXp() == 1 && skills.getForehand().getLevel() > 1, "technique XP tracks the throw used");
        check(skills.launchSpeedMultiplier() > initialSpeed && skills.releaseSpreadDegrees() < initialSpread
                && skills.flightDrag() > initialDrag && skills.fadeMultiplier("forehand") < 1
                && skills.fadeMultiplier("backhand") == 1, "leveled skills affect flight");
        skills.getPower().addXp(10000);
        check(skills.getPower().getLevel() == 5 && skills.getPower().getXp() == 0, "skill cap");
        var bag = new DiscBag();
        check(bag.capacity() == 5 && bag.packed().equals(List.of(DiscModel.STARTER_PUTTER,
                DiscModel.STARTER_MIDRANGE, DiscModel.STARTER_DRIVER)), "five-slot starter bag");
        expectRejected(() -> bag.unlock(DiscModel.FLOAT), "purchase requires funds");
        expectRejected(() -> bag.pack(DiscModel.FLOAT), "packing requires ownership");
        var purchased = bag.earn(100).unlock(DiscModel.FLOAT);
        check(purchased.credits() == 40 && purchased.owns(DiscModel.FLOAT)
                && purchased.packed().equals(bag.packed()), "purchase debits once without replacing loadout");
        expectRejected(() -> purchased.unlock(DiscModel.FLOAT), "duplicate purchase rejected");
        var full = purchased.earn(1000).pack(DiscModel.FLOAT).unlock(DiscModel.ANCHOR).pack(DiscModel.ANCHOR)
                .unlock(DiscModel.GLIDER);
        check(full.packed().size() == 5 && full.packed().stream().filter(d -> d.type() == DiscType.PUTTER).count() == 3,
                "five slots allow multiple discs of a category");
        expectRejected(() -> full.pack(DiscModel.GLIDER), "full bag rejects another disc");
        var upgraded = full.upgrade().pack(DiscModel.GLIDER).moveToFront(DiscModel.GLIDER);
        check(upgraded.capacity() == 7 && upgraded.credits() == full.credits() - 180
                && upgraded.packed().getFirst() == DiscModel.GLIDER, "upgrade and order");
        check(upgraded.upgrade().capacity() == 9, "second upgrade");
        expectRejected(() -> upgraded.upgrade().upgrade(), "highest upgrade cannot charge again");
        var onlyOne = bag.unpack(DiscModel.STARTER_MIDRANGE).unpack(DiscModel.STARTER_DRIVER);
        expectRejected(() -> onlyOne.unpack(DiscModel.STARTER_PUTTER), "last disc stays packed");
        var date = java.time.Instant.parse("2026-02-02T00:00:00Z");
        var catalog = DiscModel.builtIns();
        var stock = DiscStock.at(date, 24, catalog);
        check(stock.offers().size() == 3 && Collections.disjoint(stock.offers(), DiscStock.at(date.plusSeconds(86400), 24, catalog).offers()),
                "different stock after a UTC rotation");
        check(DiscStock.at(stock.restockAt().minusSeconds(1), 24, catalog).offers().equals(stock.offers())
                && DiscStock.at(date, 0, catalog).offers().size() == catalog.size(), "stable stock and optional rotation");
        var locker = new DecalLocker();
        expectRejected(() -> locker.equip(ScoreDecal.GOLD_RING), "locked decal cannot equip");
        var unlockedDecal = locker.unlock(ScoreDecal.GOLD_RING);
        expectRejected(() -> unlockedDecal.unlock(ScoreDecal.GOLD_RING), "decal cannot be bought twice");
        check(unlockedDecal.equip(ScoreDecal.GOLD_RING).equip(ScoreDecal.NONE).owns(ScoreDecal.GOLD_RING),
                "clearing a decal keeps ownership");
        check(new DecalLocker(Set.of(), ScoreDecal.EMBER_BLOOM).equipped() == ScoreDecal.NONE,
                "invalid saved selection falls back to no decal");
        check(purchased.spend(20).credits() == 20 && purchased.spend(20).owns(DiscModel.FLOAT),
                "decal checkout shares the wallet and leaves discs intact");
        check(DiscFlightEngine.ratedLaunchSpeed(DiscModel.SWITCHBACK.createDisc(), 2.5)
                > DiscFlightEngine.ratedLaunchSpeed(DiscModel.STARTER_DRIVER.createDisc(), 2.5), "speed affects launch");
        var technique = new BackhandTechnique();
        var glider = new Vector(2, 0, 0);
        var cornerstone = glider.clone();
        technique.applyPhysics(DiscModel.GLIDER.createDisc(), glider, 25, 100, BlockFace.EAST);
        technique.applyPhysics(DiscModel.CORNERSTONE.createDisc(), cornerstone, 25, 100, BlockFace.EAST);
        check(glider.getY() > cornerstone.getY() && glider.getZ() > cornerstone.getZ(), "glide and high-speed turn affect flight");
        glider = new Vector(1, 0, 0); cornerstone = glider.clone();
        technique.applyPhysics(DiscModel.GLIDER.createDisc(), glider, 95, 100, BlockFace.EAST);
        technique.applyPhysics(DiscModel.CORNERSTONE.createDisc(), cornerstone, 95, 100, BlockFace.EAST);
        check(cornerstone.getZ() < glider.getZ(), "fade affects the late flight path");
        for (int holes : new int[]{1,3,9,18}) for(long seed : new long[]{1,2026,999}) {
            CourseGrid grid = new CourseGrid(23,((holes+2)/3)*7+2,List.of(TileTypes.values()),holes,seed);
            grid.generate(Division.JUNIOR);
            check(grid.getTeePoints().size()==holes && grid.getHolePoints().size()==holes,"complete hole anchors");
            Set<CourseGrid.GridPoint> anchors=new HashSet<>(grid.getTeePoints().values()); anchors.addAll(grid.getHolePoints().values());
            check(anchors.size()==holes*2,"unique tees and baskets");
            Set<Long> lengths = new HashSet<>();
            for(int hole=0;hole<holes;hole++) {
                var tee=grid.getTeePoints().get(hole); var pin=grid.getHolePoints().get(hole);
                lengths.add(Math.round(Math.hypot(pin.x()-tee.x(),pin.z()-tee.z())));
                check(grid.surfaceHeight(tee.x(),tee.z())==65 && grid.surfaceHeight(pin.x(),pin.z())==65,"level tee and basket");
                check(grid.getHoleRoutePoints().get(hole).size()>=2 && grid.getHolePars().get(hole)>=3,"playable route and par");
                for(var point:grid.getHoleRoutePoints().get(hole)) check(grid.protectedLane(point.x()+8,point.z()+8),"protected centerline");
            }
            if (holes == 18) {
                check(lengths.size() == 6 && Collections.min(lengths) < 40 && Collections.max(lengths) > 110,
                        "six distinct tee distances across an 18-hole round");
                check(Set.copyOf(grid.getHolePars().values()).equals(Set.of(3, 4, 5)), "multiple pars");
            }
            var same=new CourseGrid(23,((holes+2)/3)*7+2,List.of(TileTypes.values()),holes,seed); same.generate(Division.JUNIOR);
            check(grid.getTeePoints().equals(same.getTeePoints()) && grid.getHoleRoutePoints().equals(same.getHoleRoutePoints()), "repeatable hole routing");
            for(int x=0;x<23;x++) for(int z=0;z<grid.getDepth();z++)
                check(grid.getTile(x,z).getCollapsedState()==same.getTile(x,z).getCollapsedState(),"repeatable seed");
        }
        var preview = new normalmanv2.normalDiscGolf.impl.round.HolePreviewPath(List.of(
                new org.bukkit.Location(null,0,65,0), new org.bukkit.Location(null,32,65,0),
                new org.bukkit.Location(null,32,65,32), new org.bukkit.Location(null,64,65,32)));
        org.bukkit.Location previous = null;
        for (int i=0;i<=240;i++) {
            var frame=preview.frame(i/240.0);
            check(Float.isFinite(frame.getPitch()) && frame.getPitch()<=35 && frame.getPitch()>=-10,"bounded camera pitch");
            if(previous!=null) check(previous.toVector().distance(frame.toVector())<2,"smooth distance-paced camera");
            previous=frame;
        }
        check(previous.toVector().distance(new org.bukkit.util.Vector(64,65,32))>15,"camera stops before basket");
        System.out.println("PASS: scoring, flight, Parkland fixtures, six terrain profiles, seeded variation, and course configuration.");
    }
    private static void check(boolean condition,String name) { if(!condition) throw new AssertionError(name); }

    private static void checkRankedSeasons() throws java.io.IOException {
        var config = new MemoryConfiguration();
        config.set("ranked-seasons.minimum-rounds", 2);
        var start = java.time.Instant.parse("2026-11-01T00:00:00Z");
        var season = RankedSeason.open(1, start, config);
        check(season.includes(start) && season.includes(season.ends().minusNanos(1)) && !season.includes(season.ends()),
                "the end instant belongs to the next season");
        var first = UUID.randomUUID();
        var tie = UUID.randomUUID();
        var third = UUID.randomUUID();
        var provisional = UUID.randomUUID();
        season = season.record(List.of(
                new RankedSeason.Score(first, "Ace", 1000, 9),
                new RankedSeason.Score(tie, "Birdie", 950, 18),
                new RankedSeason.Score(third, "Chains", 925, 18),
                new RankedSeason.Score(provisional, "Newcomer", 1200, 18)));
        check(season.standings().isEmpty(), "ratings are provisional until the required number of rounds");
        season = season.record(List.of(
                new RankedSeason.Score(first, "Ace", 925, 18),
                new RankedSeason.Score(tie, "Birdie", 950, 9),
                new RankedSeason.Score(third, "Chains", 925, 9)));
        var standings = season.standings();
        check(standings.stream().map(row -> row.place()).toList().equals(List.of(1, 1, 3))
                && standings.getFirst().entry().rating() == 950 && season.prize(1) == 300 && season.prize(3) == 100,
                "hole-weighted ratings share tied places and their prizes");
        config.set("ranked-seasons.length-days", 10);
        config.set("ranked-seasons.minimum-rounds", 4);
        config.set("ranked-seasons.prizes", List.of(500));
        var next = season.followAt(season.ends(), config);
        check(next.number() == 2 && next.starts().equals(season.ends()) && next.minimumRounds() == 4
                && next.entries().isEmpty() && next.prize(1) == 500 && season.minimumRounds() == 2,
                "rollover opens an empty board with the new settings");
        var recovered = season.followAt(season.ends().plus(java.time.Duration.ofDays(21)), config);
        check(recovered.number() == 4 && recovered.starts().equals(season.ends().plus(java.time.Duration.ofDays(20))),
                "downtime skips empty seasons without shifting the schedule");
        var directory = java.nio.file.Files.createTempDirectory("ndg-season-check-");
        var file = directory.resolve("ranked-seasons.yml");
        try {
            var store = new RankedSeasonStore(file);
            var archive = new TreeMap<>(Map.of(1, season.markPrizesPaid(), 2, next));
            store.save(archive);
            check(store.load().equals(archive), "current and archived standings, prizes, and payment state survive restart");
        } finally {
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.delete(directory);
        }
    }

    private static void checkWeeklyTournament() throws java.io.IOException {
        var config = new MemoryConfiguration();
        config.set("weekly-tournament.day", "saturday");
        config.set("weekly-tournament.time-utc", "20:00:30");
        var now = java.time.Instant.parse("2026-11-27T16:00:00Z");
        var definition = WeeklyTournament.next(config, now);
        check(definition.start().equals(java.time.Instant.parse("2026-11-28T20:00:30Z"))
                && WeeklyTournament.next(config, definition.start().plusSeconds(1)).start()
                .equals(definition.start().plus(java.time.Duration.ofDays(7))), "weekly schedule repeats at the same UTC time");
        var event = WeeklyTournament.scheduled(definition);
        check(!event.registrationOpen(definition.opens().minusSeconds(1)) && event.registrationOpen(definition.opens())
                && !event.registrationOpen(definition.start()), "registration uses the advertised window");
        var alice = UUID.randomUUID();
        var bob = UUID.randomUUID();
        var casey = UUID.randomUUID();
        event = event.withEntrant(alice, "Alice").withEntrant(bob, "Bob").withEntrant(casey, "Casey");
        check(event.withEntrant(casey, null).entrants().size() == 2, "withdrawing frees a registration slot");
        var directory = java.nio.file.Files.createTempDirectory("ndg-weekly-check-");
        var file = directory.resolve("weekly-tournaments.yml");
        try {
            var store = new WeeklyTournamentStore(file);
            store.save(Map.of(definition.start(), event));
            check(store.load().get(definition.start()).equals(event), "registrations survive reload");
            var complete = event.start(Set.of(alice, bob)).finish(List.of(
                    new StrokeStandings.Entry(alice, "Alice", 22, -5), new StrokeStandings.Entry(bob, "Bob", 22, -5)));
            var prizes = StrokeStandings.rank(complete.results()).stream()
                    .map(row -> definition.prize(row.place())).toList();
            check(prizes.equals(List.of(100, 100)) && !complete.starters().contains(casey),
                    "equal stroke totals get the same place and prize, no-shows stay distinguishable");
            store.save(Map.of(definition.start(), complete.markPrizesPaid()));
            check(store.load().get(definition.start()).equals(complete.markPrizesPaid()), "standings and prize state survive reload");
        } finally {
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.deleteIfExists(directory);
        }
    }

    private static void checkDailyCompetition() throws java.io.IOException {
        var day = java.time.LocalDate.parse("2026-11-26");
        var fileDirectory = java.nio.file.Files.createTempDirectory("ndg-daily-check-");
        var config = new MemoryConfiguration();
        config.set("daily-course.holes", 2);
        config.set("course.terrain", "parkland");
        config.set("course.seed-mode", "fixed");
        config.set("course.seed", 123);
        var clock = java.time.Clock.fixed(day.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().minusSeconds(1),
                java.time.ZoneId.of("Asia/Tokyo"));
        try {
            var service = new DailyCompetition(fileDirectory, () -> config, clock);
            var definition = service.today().course();
            check(definition.date().equals(day) && definition.seed() != 123 && definition.holes() == 2,
                    "UTC Daily Course uses its own date and seed");
            var one = new DailyCourse(definition, Division.JUNIOR, "one");
            var two = new DailyCourse(definition, Division.JUNIOR, "two");
            one.grid().generate(Division.JUNIOR);
            two.grid().generate(Division.JUNIOR);
            check(one.teeLocations().equals(two.teeLocations()) && one.holeLocations().equals(two.holeLocations())
                    && one.holePars().equals(two.holePars()), "independently generated daily worlds share anchors and par");
            check(one.holePars().values().stream().allMatch(par -> par == 3), "the short daily layout uses stroke-play par");
            var first = UUID.randomUUID();
            var second = UUID.randomUUID();
            var third = UUID.randomUUID();
            service.submit(definition, List.of(new StrokeStandings.Entry(first, "Ace", 3, -3),
                    new StrokeStandings.Entry(second, "Birdie", 4, -2), new StrokeStandings.Entry(third, "Chains", 5, -1)));
            var attempts = service.submit(definition, List.of(new StrokeStandings.Entry(first, "Ace", 7, 1),
                    new StrokeStandings.Entry(second, "Birdie", 3, -3)));
            check(attempts.standings().stream().map(StrokeStandings.Standing::place).toList().equals(List.of(1, 1, 3))
                    && attempts.entries().get(first).strokes() == 3 && attempts.entries().size() == 3,
                    "daily results keep one best score per player and give ties equal place");
            var round = new DailyRound(one, "one", new DefaultRoundSettings(8, RoundType.RECREATIONAL, Division.JUNIOR, false));
            var card = new ScoreCard();
            card.recordParticipation(first);
            card.recordBasket(0, 1, 3, first, 16);
            card.recordHoleScore(1, 12, 3);
            check(!round.eligible(card, first), "concessions and stroke-limit scores do not appear on the official board");
            card = new ScoreCard();
            card.recordParticipation(first);
            card.recordBasket(0, 1, 3, first, 16);
            card.recordBasket(1, 2, 3, first, 4);
            check(round.eligible(card, first) && !round.eligible(card, second), "only full participated cards qualify");

            var flight = List.of(DailyGhost.Point.at(56.5, 67.625, 56.875), DailyGhost.Point.at(88.45, 67.125, 72.5));
            var ace = new DailyGhost.Hole(1, List.of(flight));
            var birdie = new DailyGhost.Hole(2, List.of(flight, flight.reversed()));
            var archived = new DailyGhost(List.of(ace, birdie));
            var tied = new DailyGhost(List.of(birdie, ace));
            var previous = attempts.entries().get(first);
            check(service.ghost(attempts, first) == null && service.saveGhost(attempts, previous, archived),
                    "matching a legacy daily best supplies its first recording");
            check(!service.saveGhost(attempts, previous, tied) && service.ghost(attempts, first).equals(archived),
                    "a tie retains the original per-hole card and paths");
            check(!service.saveGhost(attempts, new StrokeStandings.Entry(first, "Ace", 4, -2), archived),
                    "a worse result cannot touch the best ghost");
            var improvement = new StrokeStandings.Entry(first, "Ace", 2, -4);
            attempts = service.submit(definition, List.of(improvement));
            var improved = new DailyGhost(List.of(ace, ace));
            check(service.ghost(attempts, first) == null && service.saveGhost(attempts, improvement, improved),
                    "a lower score selects a new recording without reusing the old paths");
            config.set("daily-course.holes", 1);
            config.set("course.terrain", "desert");
            var restarted = new DailyCompetition(fileDirectory, () -> config, clock);
            check(restarted.today().equals(attempts), "restarting with changed configuration preserves the in-progress day's board");
            check(restarted.ghost(restarted.today(), first).equals(improved) && restarted.ghost(restarted.today(), second) == null,
                    "stored paths survive reload and are selected by UUID");
            var tomorrow = new DailyCompetition(fileDirectory, () -> config, java.time.Clock.offset(clock, java.time.Duration.ofSeconds(1)));
            check(tomorrow.today().course().date().equals(day.plusDays(1)) && tomorrow.today().course().seed() != definition.seed()
                    && tomorrow.today().course().holes() == 1 && tomorrow.today().entries().isEmpty(), "UTC midnight starts a new seed and board");
            check(tomorrow.ghost(tomorrow.today(), first) == null && tomorrow.ghost(tomorrow.board(day), first).equals(improved),
                    "ghosts remain on their original UTC course across midnight");
            var themed = new DailyCourse(tomorrow.today().course(), Division.JUNIOR, "themed");
            themed.grid().generate(Division.JUNIOR);
            check(themed.holePars().get(0) == 3, "themed daily holes also use stroke-play par");
            tomorrow.submit(definition, List.of(new StrokeStandings.Entry(third, "Chains", 4, -2)));
            check(tomorrow.board(day).entries().get(third).strokes() == 4 && tomorrow.today().entries().isEmpty(),
                    "finishing yesterday's round after midnight updates only the original board");
        } finally {
            try (var files = java.nio.file.Files.walk(fileDirectory)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
            }
        }
    }

    private static void checkRoundAnalytics() {
        var player = new RoundAnalytics();
        player.recordThrow(0, true, false, RoundAnalytics.Finish.FAULT, 60);
        player.recordPenalty();
        player.recordThrow(0, true, false, RoundAnalytics.Finish.FAIRWAY, 90);
        player.recordThrow(0, false, true, RoundAnalytics.Finish.OTHER, 5);
        player.recordThrow(0, false, true, RoundAnalytics.Finish.BASKET, 10.5);
        player.recordThrow(1, true, false, RoundAnalytics.Finish.FAIRWAY, 32);
        player.recordThrow(2, true, false, RoundAnalytics.Finish.BASKET, 30);
        player.recordThrow(3, true, true, RoundAnalytics.Finish.OTHER, 6);
        player.recordThrow(3, true, false, RoundAnalytics.Finish.FAIRWAY, 40);
        var split = player.snapshot();
        check(split.drives() == 3 && split.fairwayHits() == 2 && split.longestDrive().orElseThrow() == 32,
                "first non-putting tee attempts count, OB and retries cannot improve longest drive");
        check(split.puttAttempts() == 3 && split.puttsMade() == 1 && split.longestPutt().orElseThrow() == 10.5
                && split.obPenalties() == 1, "putts and penalties keep independent personal totals");
        var teammate = new RoundAnalytics();
        teammate.recordThrow(1, false, false, RoundAnalytics.Finish.OTHER, 50);
        var sharedCard = "Team score: -1 (11 strokes, par 12) • 4/4 holes";
        var summary = new RoundSummary("Doubles | Parkland", sharedCard, split, true);
        var other = new RoundSummary("Doubles | Parkland", sharedCard, teammate.snapshot(), true);
        check(summary.detailLines().contains("Fairway hits: 2/3 (67%)")
                && summary.detailLines().contains("Putting accuracy: 1/3 (33%)")
                && other.detailLines().contains("Fairway hits: — (no drives)")
                && other.detailLines().contains("Putting accuracy: — (no putts)")
                && other.detailLines().getFirst().equals(summary.detailLines().getFirst()),
                "players share the team score and keep separate throw statistics");
        var custom = new RoundSummary("FFA | Saved Layout", "Score: 0", split, false);
        check(custom.detailLines().contains("Fairway hits: — (no marked fairways)"),
                "custom layouts without fairway markings report no fairway percentage");
    }

    private static void checkCourseEventSettings() {
        var config = new MemoryConfiguration();
        config.set("course-events.chance-percent", 100);
        config.set("course-events.weights.thunderstorm", 0);
        config.set("course-events.weights.night-golf", 0);
        var onlyFog = CourseEventSettings.read(config);
        check(onlyFog.forHole(41, 0, false) == CourseEvent.FOG, "weights can exclude individual events");
        check(onlyFog.forHole(41, 0, true) == CourseEvent.CLEAR, "rated events are opt-in");
        config.set("course-events.weights.fog", 0);
        check(CourseEventSettings.read(config).forHole(41, 0, false) == CourseEvent.CLEAR, "zero weights leave the hole clear");
        config.set("course-events.chance-percent", 0);
        config.set("course-events.weights.fog", 1);
        check(CourseEventSettings.read(config).forHole(41, 0, false) == CourseEvent.CLEAR, "zero chance stops random events");
        config.set("course-events.include-rated", true);
        config.set("course-events.sequence", List.of("thunderstorm", "fog", "night-golf", "clear"));
        var scheduled = CourseEventSettings.read(config);
        check(scheduled.forHole(41, 0, true) == CourseEvent.THUNDERSTORM
                && scheduled.forHole(41, 1, true) == CourseEvent.FOG
                && scheduled.forHole(41, 2, true) == CourseEvent.NIGHT_GOLF
                && scheduled.forHole(41, 3, true) == CourseEvent.CLEAR
                && scheduled.forHole(41, 4, true) == CourseEvent.THUNDERSTORM, "explicit sequence loops in rated formats when enabled");
        config.set("course-events.enabled", false);
        check(CourseEventSettings.read(config).forHole(41, 0, false) == CourseEvent.CLEAR, "disabled events override the sequence");
        var storm = HoleWind.thunderstorm(41, 0, 1);
        check(storm.equals(HoleWind.thunderstorm(41, 0, 1)) && storm.strength() > HoleWind.forHole(41, 0).strength(),
                "storm wind is reproducible and stronger than fair-weather wind");
        var early = new Vector();
        DiscTrajectory.applyReleasePhysics(early, 1, ShotSetup.Angle.FLAT, false, false, storm);
        double directionChange = 0, strengthChange = 0;
        for (int tick = 5; tick <= 60; tick += 5) {
            var later = new Vector();
            DiscTrajectory.applyReleasePhysics(later, tick, ShotSetup.Angle.FLAT, false, false, storm);
            directionChange = Math.max(directionChange, early.angle(later));
            strengthChange = Math.max(strengthChange, Math.abs(early.length() - later.length()));
        }
        check(directionChange > .1 && strengthChange > .001,
                "storm gusts change strength and direction during flight");
    }

    private static void checkArcadeFlight() {
        var origin = new org.bukkit.Location(null, 0, 66, 0);
        var rocket = new DiscTrajectory(origin, new Vector(1, 0, 0), 30, (from, velocity) -> null,
                (velocity, tick) -> {}, new normalmanv2.normalDiscGolf.impl.disc.GroundPhysics(false, 0, 0),
                new ArcadeFlight(ArcadeDisc.ROCKET));
        for (int tick = 1; tick < 5; tick++)
            check(rocket.advance().location().getX() == tick && rocket.arcadeEffect() == null, "rocket starts at ordinary launch speed");
        check(Math.abs(rocket.advance().location().getX() - 5.42) < .0001 && rocket.arcadeEffect() == ArcadeDisc.ROCKET,
                "rocket increases mid-flight velocity");
        check(Math.abs(rocket.advance().location().getX() - 6.84) < .0001 && rocket.arcadeEffect() == null,
                "rocket applies one boost");
        var contact = new normalmanv2.normalDiscGolf.impl.disc.util.MathUtil.FlightHit(origin,
                normalmanv2.normalDiscGolf.impl.disc.util.MathUtil.HitType.BLOCK, org.bukkit.Material.STONE, BlockFace.UP);
        var ground = new normalmanv2.normalDiscGolf.impl.disc.GroundPhysics(false, 0, 0);
        var ordinary = new DiscTrajectory(origin, new Vector(1, -.1, 0), 30, (from, velocity) -> contact,
                (velocity, tick) -> {}, ground);
        var sticky = new DiscTrajectory(origin, new Vector(1, -.1, 0), 30, (from, velocity) -> contact,
                (velocity, tick) -> {}, new normalmanv2.normalDiscGolf.impl.disc.GroundPhysics(false, 0, 0),
                new ArcadeFlight(ArcadeDisc.STICKY));
        check(!ordinary.advance().finished() && sticky.advance().finished() && sticky.arcadeEffect() == ArcadeDisc.STICKY,
                "sticky stops at the exact contact that would have skipped with a normal disc");
    }

    private static void checkCareer() {
        int[] thresholds = {0, 800, 850, 900, 950, 1000};
        for (var division : CareerDivision.values()) {
            int ordinal = division.ordinal(), threshold = thresholds[ordinal];
            check(division.minimum() == threshold && CareerDivision.forRating(threshold) == division,
                    "the division begins at its rating threshold");
            if (ordinal > 0) check(CareerDivision.forRating(threshold - 1) == CareerDivision.values()[ordinal - 1],
                    "the prior division ends immediately before the threshold");
        }
        var rating = new PDGARating();
        var promotion = rating.recordRound(-2, 1, 0);
        check(promotion.before() == 0 && promotion.after() == 824 && promotion.division() == CareerDivision.MA3,
                "an ace earns a weighted first career rating");
        rating.setRating(950);
        var lower = rating.recordRound(1, 1, 50);
        check(lower.after() == 927 && lower.division() == CareerDivision.MA1 && rating.getPeakRating() == 950
                && rating.getLastRoundRating() == 792, "a weaker round can lower division and keeps the peak");
        var hard = new PDGARating().recordRound(0, 18, 75);
        var easy = new PDGARating().recordRound(0, 18, 25);
        check(hard.roundRating() > easy.roundRating() && hard.after() > easy.after(), "ratings adjust for generated course difficulty");
        check(new PDGARating().recordRound(0, 18, 50).after() > new PDGARating().recordRound(0, 1, 50).after(),
                "longer scorecards carry more weight");

        var skills = new PlayerSkills(1, 5);
        var context = new PlayerSkills.PracticeContext(true, true, true);
        for (String id : List.of("putting", "recovery", "wind-reading", "mental-focus"))
            skills.asMap().get(id).restoreProgress(1, 64);
        var gains = skills.practice(DiscType.PUTTER, "forehand", false, true, Stance.PUTT, context);
        for (String id : List.of("putting", "recovery", "wind-reading", "mental-focus"))
            check(gains.stream().anyMatch(gain -> gain.skill().equals(id) && gain.leveledUp()), id + " levels from the matching shot");
        check(skills.timingError(.6, Stance.PUTT, true) == 0 && skills.timingError(.6, Stance.NORMAL, false) > 0,
                "putting and focus make the pressure putt timing window wider");
        check(skills.releaseSpreadDegrees(true) < skills.releaseSpreadDegrees(), "recovery steadies a rough lie");
        var wind = new HoleWind(22.5, 1.3, 0);
        check(wind.description(2).contains("SSW") && wind.description(4).contains("1.3"),
                "wind reading improves directional and strength detail");
    }

    private static void checkThemedCourses() {
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("course.seed-mode", "fixed");
        config.set("course.seed", 4102L);
        config.set("course.enabled-terrains", List.of("mountain"));
        config.set("course.terrains.mountain.fairway-width", 28);
        config.set("course.terrains.mountain.obstacle-density", 0.0);
        config.set("course.terrains.mountain.archetypes", List.of("gap-shot", "dogleg"));
        long seed = CourseGenerationSettings.seed(config);
        var selected = CourseGenerationSettings.read(config, seed);
        check(seed == 4102L && selected.terrain() == CourseTerrain.MOUNTAIN && selected.fairwayWidth() == 28
                && selected.obstacleDensity() == 0 && selected.archetypes().equals(List.of(HoleArchetype.GAP_SHOT, HoleArchetype.DOGLEG)),
                "fixed seed, terrain pool, and per-terrain overrides work together");
        config.set("course.seed-mode", "random");
        check(CourseGenerationSettings.seed(config) != CourseGenerationSettings.seed(config), "random mode makes fresh seeds");

        for (CourseTerrain terrain : CourseTerrain.environments()) {
            var profile = CourseGenerationSettings.defaults(terrain);
            var a = themed(profile, 10, 41L, CourseGrid.Layout.PARKLAND);
            var repeat = themed(profile, 10, 41L, CourseGrid.Layout.PARKLAND);
            var b = themed(profile, 10, 83L, CourseGrid.Layout.PARKLAND);
            check(a.getTeePoints().equals(repeat.getTeePoints()) && a.getHoleRoutePoints().equals(repeat.getHoleRoutePoints()),
                    terrain + " repeats with the same seed");
            check(!a.getTeePoints().equals(b.getTeePoints()) && !a.getHoleRoutePoints().equals(b.getHoleRoutePoints()),
                    terrain + " changes tee and route geometry with the seed");
            var names = new HashSet<String>();
            var distances = new HashSet<Long>();
            for (int hole = 0; hole < 10; hole++) {
                var tee = a.getTeePoints().get(hole);
                var pin = a.getHolePoints().get(hole);
                names.add(a.holeName(hole));
                distances.add(Math.round(Math.hypot(pin.x() - tee.x(), pin.z() - tee.z())));
                check(!a.surfaceType(tee.x(), tee.z()).isHazard() && !a.surfaceType(pin.x(), pin.z()).isHazard(),
                        terrain + " provides safe anchors");
                check(a.surfaceHeight(tee.x(), tee.z()) + 1 == tee.y() && a.surfaceHeight(pin.x(), pin.z()) + 1 == pin.y(),
                        terrain + " anchors match the world surface");
                if (a.holeName(hole).equals("Double Fairway") || a.holeName(hole).equals("Risk vs. Reward"))
                    check(!a.alternateLane(hole).isEmpty(), "branching archetypes have a second route");
            }
            check(names.size() == HoleArchetype.values().length && distances.size() >= 6
                    && a.difficultyRating() > 0 && a.difficultyRating() <= 100, terrain + " offers varied lengths, archetypes and difficulty");
            var shortCourse = themed(profile, 6, 83L, CourseGrid.Layout.SHORT);
            for (int hole = 0; hole < 6; hole++) {
                var tee = shortCourse.getTeePoints().get(hole);
                var pin = shortCourse.getHolePoints().get(hole);
                check(Math.hypot(pin.x() - tee.x(), pin.z() - tee.z()) < 49 && shortCourse.getHolePars().get(hole) == 1,
                        terrain + " preserves single-throw distances");
            }
        }
        var flat = themed(new CourseGenerationSettings(CourseTerrain.WOODLAND, 0, 28, 0, 0,
                List.of(HoleArchetype.TUNNEL)), 1, 41L, CourseGrid.Layout.PARKLAND);
        var tight = themed(new CourseGenerationSettings(CourseTerrain.WOODLAND, 0, 8, 1, 0,
                List.of(HoleArchetype.TUNNEL)), 1, 41L, CourseGrid.Layout.PARKLAND);
        check(flat.getTeePoints().equals(tight.getTeePoints()) && flat.difficultyRating() < tight.difficultyRating(),
                "width and obstacles tune difficulty independently of the layout seed");
        for (int x = 16; x < 128; x++) for (int z = 16; z < 128; z++)
            check(flat.surfaceHeight(x, z) == 70 && !flat.surfaceType(x, z).isHazard(),
                    "zero relief and hazard density produce flat dry land");
        var islandGreen = themed(new CourseGenerationSettings(CourseTerrain.WOODLAND, 4, 14, .2, 1,
                List.of(HoleArchetype.ISLAND_GREEN)), 1, 41L, CourseGrid.Layout.PARKLAND);
        var pin = islandGreen.getHolePoints().get(0);
        check(!islandGreen.surfaceType(pin.x(), pin.z()).isHazard()
                && islandGreen.surfaceType(pin.x(), pin.z() - 10) == TileTypes.WATER
                && islandGreen.surfaceType(pin.x(), pin.z() + 10) == TileTypes.WATER,
                "an island green surrounds solid basket footing with a water carry");
    }

    private static void checkWoodlandLandmarks() {
        var profile = CourseGenerationSettings.defaults(CourseTerrain.WOODLAND);
        var original = themed(profile, 3, 2026L, CourseGrid.Layout.PARKLAND);
        var repeat = themed(profile, 3, 2026L, CourseGrid.Layout.PARKLAND);
        var another = themed(profile, 3, 2027L, CourseGrid.Layout.PARKLAND);
        check(!original.getHoleRoutePoints().equals(another.getHoleRoutePoints()), "Woodland routes change with the seed");
        for (int hole = 0; hole < 3; hole++)
            check(original.woodlandLandmarks(hole).equals(repeat.woodlandLandmarks(hole)), "Woodland landmarks repeat by seed");

        for (int width : new int[]{8, 13, 28}) {
            var tuned = new CourseGenerationSettings(CourseTerrain.WOODLAND, 7, width, .68, 1, List.of());
            var doglegHints = new HashSet<String>();
            for (long seed = 0; seed < 48; seed++) {
                var course = themed(tuned, 3, seed, CourseGrid.Layout.PARKLAND);
                var layouts = new HashSet<WoodlandHole>();
                for (int hole = 0; hole < 3; hole++) {
                    var layout = course.woodlandHole(hole);
                    check(layout != null && layouts.add(layout), "first three Woodland holes feature distinct layouts");
                    var points = course.woodlandLandmarks(hole);
                    int expected = switch (layout) {
                        case CATHEDRAL_RUN -> 4;
                        case FERNBEND -> 1;
                        case MOSSGATE -> 2;
                    };
                    check(points.size() == expected, layout + " places every landmark on seed " + seed + " at width " + width);
                    for (var mark : points) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                        int x = mark.position().x() + dx, z = mark.position().z() + dz;
                        check(!course.protectedLane(x, z) && !course.surfaceType(x, z).isHazard(),
                                layout + " landmark footprint respects the throwing lane and dry ground");
                    }
                    if (layout == WoodlandHole.FERNBEND) doglegHints.add(layout.hint(course.centerline(hole)));
                }
            }
            check(doglegHints.equals(Set.of("Aim for the bend, then turn left.", "Aim for the bend, then turn right.")),
                    "Fernbend reports both seeded turn directions");
        }
        var minimal = new CourseGenerationSettings(CourseTerrain.WOODLAND, 0, 13, 0, 0, List.of(HoleArchetype.GAP_SHOT));
        var noObstacles = themed(minimal, 1, 2026, CourseGrid.Layout.PARKLAND);
        check(noObstacles.woodlandHole(0) == WoodlandHole.MOSSGATE && noObstacles.woodlandLandmarks(0).isEmpty(),
                "allowed archetypes and zero obstacle density apply to showcased holes");
        var daily = themed(profile, 3, 2026, CourseGrid.Layout.DAILY);
        for (int hole = 0; hole < 3; hole++)
            check(daily.woodlandHole(hole) == null && daily.woodlandLandmarks(hole).isEmpty(),
                    "existing competition layouts keep their original generation path");
    }

    private static CourseGrid themed(CourseGenerationSettings settings, int holes, long seed, CourseGrid.Layout layout) {
        var grid = new CourseGrid(23, ((holes + 2) / 3) * 7 + 2, List.of(TileTypes.values()), holes, seed, layout, settings);
        grid.generate(Division.JUNIOR);
        return grid;
    }

    private static void checkAceRaceCourses() {
        for (long seed : new long[]{1, 41, 2026, 999}) {
            var grid = new CourseGrid(23, 16, List.of(TileTypes.values()), 6, seed, CourseGrid.Layout.SHORT);
            grid.generate(Division.JUNIOR);
            var distances = new HashSet<Long>();
            var anchors = new HashSet<CourseGrid.GridPoint>();
            for (int hole = 0; hole < 6; hole++) {
                var tee = grid.getTeePoints().get(hole);
                var pin = grid.getHolePoints().get(hole);
                check(anchors.add(tee) && anchors.add(pin), "unique Ace Race anchors");
                distances.add(Math.round(Math.hypot(pin.x() - tee.x(), pin.z() - tee.z())));
                check(grid.getHolePars().get(hole) == 1, "Ace Race has one-throw targets");
                for (var point : grid.getHoleRoutePoints().get(hole))
                    check(grid.protectedLane(point.x() + 8, point.z() + 8), "Ace Race has a protected line to the basket");
            }
            check(distances.equals(Set.of(23L, 32L, 36L, 48L)), "Ace Race mixes four short tee distances");
        }
    }

    private static void checkClosestToPin() {
        var basket = new org.bukkit.Location(null, 20.5, 66.9, 10.5);
        var landing = new org.bukkit.Location(null, 23.5, 62, 14.5);
        check(ClosestToPinRound.distanceTenths(landing, basket) == 50, "pin distance measures the settled horizontal position");
        var near = OptionalInt.of(ClosestToPinRound.distanceTenths(basket.clone().add(.12, 0, 0), basket));
        var equal = OptionalInt.of(ClosestToPinRound.distanceTenths(basket.clone().add(.13, 0, 0), basket));
        check(near.getAsInt() == 1 && near.equals(equal), "pin scoring uses the same tenth-block precision as the displayed distances");
        var better = ClosestToPinRound.Total.from(List.of(near, OptionalInt.of(0)));
        var worse = ClosestToPinRound.Total.from(List.of(equal, OptionalInt.of(1)));
        var fault = ClosestToPinRound.Total.from(List.of(OptionalInt.of(0), OptionalInt.empty()));
        check(better.compareTo(worse) < 0 && worse.compareTo(fault) < 0, "pin ranking compares faults before cumulative distance");
        var trajectory = new DiscTrajectory(landing, new Vector(1, 1, 0), 0, (from, velocity) -> null,
                (velocity, tick) -> {}, new normalmanv2.normalDiscGolf.impl.disc.GroundPhysics(false, 0, 0));
        check(trajectory.advance().expiredInAir(), "an airborne flight limit is distinguishable from a legal landing");
    }

    private static void checkStandardDriverFlight() {
        var starter = DiscModel.STARTER_DRIVER.createDisc();
        var backhand = new BackhandTechnique();
        var forehandTechnique = new ForehandTechnique();
        var beginner = new PlayerSkills(1, 5);
        var drive = driverPath(starter, backhand, .85, beginner);
        var fullPower = driverPath(starter, backhand, 1, beginner);
        var soft = driverPath(starter, backhand, .40, beginner);
        var neutral = driverPath(new Driver(9, 5, 0, 2, "Neutral turn"), backhand, .85, beginner);
        var forehand = driverPath(starter, forehandTechnique, .85, beginner);

        check(drive.get(7).getZ() > .75 && drive.get(7).getZ() < 1.45 && drive.get(7).length() < 18,
                "the starter driver shows a moderate turn within preview range");
        check(fullPower.get(6).getZ() > drive.get(6).getZ() && Math.abs(soft.get(6).getZ()) < .15
                && Math.abs(neutral.get(6).getZ()) < .15, "release speed and turn rating distinguish flight paths");
        check(fullPower.get(30).getZ() < 15 && drive.get(30).getZ() < drive.get(24).getZ() - .25
                && soft.get(30).getZ() < -.5 && soft.get(30).getZ() > -1.25,
                "the starter driver's hard and soft throws stay controllable while retaining fade");
        check(Math.abs(forehand.get(6).getZ() + drive.get(6).getZ()) < 1e-9
                && Math.abs(forehand.get(30).getZ() + drive.get(30).getZ()) < 1e-9,
                "forehand mirrors both turn and fade");

        var practicedForm = new PlayerSkills(1, 5);
        practicedForm.getForm().addXp(10000);
        var steadierDrive = driverPath(starter, backhand, .85, practicedForm);
        check(steadierDrive.get(7).getZ() < .9 * drive.get(7).getZ() && steadierDrive.get(30).getX() > drive.get(30).getX(),
                "Form steadies early turn while preserving carry");

        var practicedForehand = new PlayerSkills(1, 5);
        practicedForehand.getForehand().addXp(10000);
        var trainedForehand = driverPath(starter, forehandTechnique, .85, practicedForehand);
        check(trainedForehand.get(4).equals(forehand.get(4)) && trainedForehand.get(30).getZ() < forehand.get(30).getZ() - .3
                && driverPath(starter, backhand, .85, practicedForehand).equals(drive),
                "technique progression softens late fade for the practiced throw");
    }

    private static List<Vector> driverPath(DiscImpl disc, ThrowTechnique technique, double power, PlayerSkills skills) {
        var origin = new Location(null, 0, 1.62, 0);
        var velocity = new Vector(DiscTrajectory.launchSpeed(disc, skills, power, false), 0, 0);
        var trajectory = new DiscTrajectory(origin, velocity, 100, (from, motion) -> null,
                (motion, tick) -> technique.applyPhysics(disc, motion, tick, 100, BlockFace.EAST,
                        skills.flightDrag(), skills.turnMultiplier(), skills.fadeMultiplier(technique.getId())),
                new GroundPhysics(false, 0, 0));
        List<Vector> path = new ArrayList<>();
        for (int tick = 0; tick < 32; tick++) {
            path.add(trajectory.advance().location().subtract(origin).toVector());
        }
        return path;
    }

    private static void checkShotSetup() {
        var hyzer = ShotSetup.Angle.HYZER;
        var anhyzer = ShotSetup.Angle.ANHYZER;
        var backhand = new Vector(1, 0, 0);
        var forehand = backhand.clone();
        var reverse = backhand.clone();
        DiscTrajectory.applyReleasePhysics(backhand, 1, hyzer, false, false, HoleWind.CALM);
        DiscTrajectory.applyReleasePhysics(forehand, 1, hyzer, true, false, HoleWind.CALM);
        DiscTrajectory.applyReleasePhysics(reverse, 1, anhyzer, false, false, HoleWind.CALM);
        check(backhand.getZ() < 0 && backhand.getZ() == -forehand.getZ()
                && forehand.equals(reverse), "hyzer, anhyzer, and handedness mirror the bank");

        var putt = new ShotSetup(hyzer, ShotSetup.Stance.PUTT, true, true);
        check(putt.usesPutting(true, 15) && !putt.usesPutting(true, 15.1)
                && !putt.usesPutting(false, 10), "putting stance is scoped to a short lie with a putter");
        var disc = DiscModel.STARTER_PUTTER.createDisc();
        var skills = new PlayerSkills(1, 5);
        check(DiscTrajectory.launchSpeed(disc, skills, 1, true)
                < DiscTrajectory.launchSpeed(disc, skills, 1, false), "putts have a slower power range");
        var wind = HoleWind.forHole(41, 0);
        check(wind.equals(HoleWind.forHole(41, 0)) && !wind.equals(HoleWind.forHole(41, 1)),
                "hole conditions repeat for the same seed and hole");
        var windShot = new Vector();
        var windPutt = new Vector();
        DiscTrajectory.applyReleasePhysics(windShot, 1, hyzer, true, false, wind);
        DiscTrajectory.applyReleasePhysics(windPutt, 1, hyzer, true, true, wind);
        check(windPutt.length() > 0 && windPutt.length() < windShot.length(), "putting resists wind drift");
        check(ShotSetup.timingError(.5, 1) == 0 && ShotSetup.timingError(1, 1) == 1
                && ShotSetup.timingError(.63, 5) < ShotSetup.timingError(.63, 1),
                "centered release and accuracy skill widen the timing window");
        skills.practice(DiscType.DRIVER, "backhand", false, false);
        check(skills.getRoller().getXp() == 0, "regular throws leave the roller skill unchanged");
        var rollGains = skills.practice(DiscType.DRIVER, "backhand", false, false, true);
        check(skills.getRoller().getXp() == 5 && rollGains.stream().filter(gain -> gain.skill().equals("roller")).count() == 1,
                "a resolved roller earns its own practice XP once");
    }

    private static void checkTrickShots() {
        var disc = DiscModel.STARTER_DRIVER.createDisc();
        var flat = ShotSetup.Angle.FLAT;
        var forward = new Vector(1, 0, 0);
        var flex = new TrickFlight(Stance.FLEX, disc, flat, false, .98);
        var mirroredFlex = new TrickFlight(Stance.FLEX, disc, flat, true, .98);
        var flexMotion = flex.aim(forward).multiply(1.5);
        var mirroredMotion = flexMotion.clone();
        for (int tick = 1; tick <= 8; tick++) {
            flex.advance(flexMotion, tick);
            mirroredFlex.advance(mirroredMotion, tick);
        }
        check(flexMotion.getZ() > .2 && Math.abs(flexMotion.getZ() + mirroredMotion.getZ()) < .00001,
                "flex opens on the anhyzer side and mirrors with technique");
        for (int tick = 9; tick <= 24; tick++) flex.advance(flexMotion, tick);
        check(flexMotion.getZ() < -.2, "late fade reverses the flex bend");

        var tomahawk = new TrickFlight(Stance.TOMAHAWK, disc, flat, false, .98);
        var thumber = new TrickFlight(Stance.THUMBER, disc, flat, false, .98);
        var leftTomahawk = new TrickFlight(Stance.TOMAHAWK, disc, flat, true, .98);
        var tomMotion = tomahawk.aim(forward).multiply(2 * tomahawk.speedScale());
        var thumbMotion = thumber.aim(forward).multiply(2 * thumber.speedScale());
        var leftMotion = tomMotion.clone();
        check(tomMotion.getY() > .9 && thumber.aim(forward).getY() > tomahawk.aim(forward).getY()
                && tomMotion.getX() > thumbMotion.getX(), "an overhand lofts high and a thumber starts steeper and shorter");
        for (int tick = 1; tick <= 21; tick++) {
            tomahawk.advance(tomMotion, tick);
            thumber.advance(thumbMotion, tick);
            leftTomahawk.advance(leftMotion, tick);
        }
        check(tomMotion.getZ() < 0 && thumbMotion.getZ() > 0 && Math.abs(leftMotion.getZ() + tomMotion.getZ()) < .00001,
                "tomahawk and thumber pan in opposite directions and follow the player's main hand");
        check(tomMotion.getY() < -.4 && thumbMotion.getY() < -.4, "both overhands drop steeply after their apex");

        var skills = new PlayerSkills(1, 5);
        skills.practice(DiscType.DRIVER, "forehand", false, false, Stance.FLEX);
        skills.practice(DiscType.DRIVER, "forehand", true, false, Stance.TOMAHAWK);
        skills.practice(DiscType.DRIVER, "forehand", false, true, Stance.THUMBER);
        check(skills.getFlex().getXp() == 5 && skills.getTomahawk().getXp() == 1 && skills.getThumber().getXp() == 10
                && skills.getForehand().getXp() == 5 && skills.getRoller().getXp() == 0,
                "trick XP respects the stance and outcome without false forehand or roller credit");
        skills.getFlex().addXp(70);
        check(TrickFlight.releaseControl(skills.stanceLevel(Stance.FLEX)) < TrickFlight.releaseControl(1)
                && skills.stanceLevel(Stance.TOMAHAWK) == 1, "raising a trick skill tightens that release");
    }

    private static void checkMatchPlay() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        check(RoundFormat.parse("match-play") == RoundFormat.MATCH && RoundFormat.parse("doubles") == RoundFormat.DOUBLES,
                "match queue alias remains distinct from doubles");
        var draw = new MatchPlayScore(first, second, 3);
        check(draw.recordStrokes(0, 4, 4).winner() == null && draw.halved() == 1, "equal strokes halve the hole");
        check(draw.recordStrokes(1, 2, 5).winner().equals(first) && draw.margin() == 1 && !draw.isComplete(),
                "one up with one to play can still be tied");
        check(draw.recordStrokes(2, 7, 6).winner().equals(second) && draw.isComplete() && draw.winner() == null,
                "match ends all square even with a different total stroke count");

        var clinch = new MatchPlayScore(first, second, 3);
        clinch.recordStrokes(0, 3, 4);
        check(clinch.award(1, second, "Hole conceded").winner().equals(first)
                && clinch.isComplete() && clinch.margin() == 2 && clinch.holesRemaining() == 1,
                "concession clinches two and one");
        try {
            clinch.recordStrokes(2, 2, 3);
            throw new AssertionError("clinched match accepted an extra hole");
        } catch (IllegalStateException expected) {
            // The match already has a winner.
        }

        var forfeit = new MatchPlayScore(first, second, 3);
        forfeit.recordStrokes(0, 2, 3);
        forfeit.forfeit(first);
        check(forfeit.isComplete() && forfeit.winner().equals(second) && forfeit.forfeiter().equals(first),
                "leaving gives the opponent the match regardless of the existing lead");
    }

    private static void checkClubhouse() {
        UUID player = UUID.randomUUID(), partner = UUID.randomUUID(), idle = UUID.randomUUID();
        var day = java.time.LocalDate.ofEpochDay(0);
        check(DailyChallenge.forDay(day) == DailyChallenge.BASKET_HUNT
                && DailyChallenge.forDay(day.plusDays(1)) != DailyChallenge.forDay(day), "daily challenge rotates");
        var card = new ScoreCard();
        card.recordParticipation(player);
        card.recordParticipation(partner);
        card.recordBasket(0, 1, 3, player, 42.7);
        card.recordBasket(1, 2, 3, partner, 20.3);
        card.recordBasket(2, 3, 3, player, 6);
        card.recordHoleScore(3, 12, 3);
        check(card.getBasketStreak() == 0 && card.getBestBasketStreak() == 3, "concession breaks the streak");
        var initial = new ClubhouseProgress();
        check(initial.recordRound(card, player, "doubles", 5, day).progress() == initial, "incomplete round pays nothing");
        check(initial.recordRound(card, idle, "doubles", 4, day).progress() == initial, "nonparticipant earns no progress");
        var first = initial.recordRound(card, player, "doubles", 4, day);
        check(first.streakCredits() == 10 && first.dailyCredits() == 0 && first.progress().dailyProgress(day) == 3,
                "streak payout and partial challenge");
        check(first.progress().baskets() == 2 && first.progress().underPar() == 1
                && first.progress().aces() == 1 && first.progress().longestBasket() == 42.7,
                "personal totals count the thrower");
        check(first.newMedals().containsAll(List.of(ClubhouseMedal.FIRST_CHAINS, ClubhouseMedal.HOT_HAND,
                        ClubhouseMedal.ACE_CARD, ClubhouseMedal.DISTANCE_MAKER)) && !first.bestCard(),
                "medals are awarded and a conceded card cannot set a record");
        var other = initial.recordRound(card, partner, "doubles", 4, day);
        check(other.progress().baskets() == 1 && other.progress().aces() == 0 && other.progress().dailyProgress(day) == 3,
                "doubles shares the challenge but attributes the ace");
        var second = first.progress().recordRound(card, player, "doubles", 4, day);
        check(second.dailyCredits() == 35 && second.newMedals().isEmpty(), "daily payout is earned and medals do not repeat");
        var third = second.progress().recordRound(card, player, "doubles", 4, day);
        check(third.dailyCredits() == 0 && third.progress().dailyProgress(day.plusDays(1)) == 0,
                "completed challenge cannot pay again and resets at midnight");
        var clean = new ScoreCard();
        clean.recordParticipation(player);
        for (int i = 0; i < 9; i++) clean.recordBasket(i, 2, 3, player, 18);
        var best = third.progress().recordRound(clean, player, "ffa", 9, day);
        check(best.bestCard() && best.progress().bestCards().get("ffa-9") == -9 && best.streakCredits() == 35
                && best.newMedals().contains(ClubhouseMedal.CLEAN_NINE), "clean card and top streak bonus");
        check(!best.progress().recordRound(clean, player, "ffa", 9, day).bestCard()
                && best.progress().recordRound(clean, player, "doubles", 9, day).bestCard(),
                "equal scores do not overwrite a record and formats stay separate");
    }

    private static void expectRejected(Runnable action, String name) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(name);
    }
}
