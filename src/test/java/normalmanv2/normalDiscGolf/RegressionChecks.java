package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.player.DiscBag;
import normalmanv2.normalDiscGolf.impl.player.DecalLocker;
import normalmanv2.normalDiscGolf.impl.cosmetic.ScoreDecal;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.disc.DiscFlightEngine;
import normalmanv2.normalDiscGolf.impl.technique.type.BackhandTechnique;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import java.util.*;

/** Dependency-free regression suite; runs as part of Gradle check. */
public final class RegressionChecks {
    public static void main(String[] args) {
        ScoreCard card = new ScoreCard();
        card.trackStroke(); card.undoStroke(); check(card.getCurrentStrokes()==0,"failed throw rolled back");
        card.trackStroke(); card.addPenalty(1); card.trackStroke();
        card.recordHoleScore(0,card.getCurrentStrokes(),3); card.resetCurrentStrokes();
        check(card.getTotalStrokes()==3 && card.getTotalScore()==0,"stroke and hazard totals");
        boolean rejected=false; try { card.recordHoleScore(0,3,3); } catch(IllegalStateException expected) { rejected=true; }
        check(rejected && card.getTotalStrokes()==3,"duplicate scoring rejected");
        for(int hole=1;hole<18;hole++) card.recordHoleScore(hole,3,3);
        check(card.getHoleScores().size()==18 && card.getTotalStrokes()==54,"full round scoring");
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
        check(bag.equipped(DiscType.DRIVER) == DiscModel.STARTER_DRIVER, "free starter loadout");
        expectRejected(() -> bag.unlock(DiscModel.FLOAT), "purchase requires funds");
        expectRejected(() -> bag.equip(DiscModel.FLOAT), "equip requires ownership");
        var purchased = bag.earn(100).unlock(DiscModel.FLOAT);
        check(purchased.credits() == 40 && purchased.owns(DiscModel.FLOAT)
                && purchased.equipped(DiscType.PUTTER) == DiscModel.STARTER_PUTTER, "purchase debits once without replacing loadout");
        expectRejected(() -> purchased.unlock(DiscModel.FLOAT), "duplicate purchase rejected");
        check(purchased.equip(DiscModel.FLOAT).equipped(DiscType.PUTTER) == DiscModel.FLOAT, "owned disc equips");
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
        var glider = new Vector(1, 0, 0);
        var cornerstone = glider.clone();
        technique.applyPhysics(DiscModel.GLIDER.createDisc(), glider, 25, 100, BlockFace.EAST);
        technique.applyPhysics(DiscModel.CORNERSTONE.createDisc(), cornerstone, 25, 100, BlockFace.EAST);
        check(glider.getY() > cornerstone.getY() && glider.getZ() < cornerstone.getZ(), "glide and early turn affect flight");
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
        System.out.println("PASS: scorecards, penalties, duplicate scores, 18-hole totals, lane geometry, and 12 seeded course layouts.");
    }
    private static void check(boolean condition,String name) { if(!condition) throw new AssertionError(name); }

    private static void expectRejected(Runnable action, String name) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(name);
    }
}
