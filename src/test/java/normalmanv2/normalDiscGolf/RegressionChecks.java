package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
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
        for (int holes : new int[]{1,3,9,18}) for(long seed : new long[]{1,2026,999}) {
            CourseGrid grid = new CourseGrid(23,((holes+2)/3)*7+2,List.of(TileTypes.values()),holes,seed);
            grid.generate(Division.JUNIOR);
            check(grid.getTeePoints().size()==holes && grid.getHolePoints().size()==holes,"complete hole anchors");
            Set<CourseGrid.GridPoint> anchors=new HashSet<>(grid.getTeePoints().values()); anchors.addAll(grid.getHolePoints().values());
            check(anchors.size()==holes*2,"unique tees and baskets");
            for(int hole=0;hole<holes;hole++) {
                var tee=grid.getTeePoints().get(hole); var pin=grid.getHolePoints().get(hole);
                check(grid.surfaceHeight(tee.x(),tee.z())==65 && grid.surfaceHeight(pin.x(),pin.z())==65,"level tee and basket");
                check(grid.getHoleRoutePoints().get(hole).size()>=2 && grid.getHolePars().get(hole)>=3,"playable route and par");
                for(var point:grid.getHoleRoutePoints().get(hole)) check(grid.protectedLane(point.x()+8,point.z()+8),"protected centerline");
            }
            var same=new CourseGrid(23,((holes+2)/3)*7+2,List.of(TileTypes.values()),holes,seed); same.generate(Division.JUNIOR);
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
}
