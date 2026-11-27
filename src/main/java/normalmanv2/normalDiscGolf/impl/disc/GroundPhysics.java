package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.impl.disc.util.MathUtil.FlightHit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public final class GroundPhysics {
    public enum Motion { AIR, SLIDE, ROLL }
    public enum Impact { NONE, SKIP, SLIDE, TREE_KICK, ROLL }

    private enum Surface {
        HARD(.74, .80, .94),
        GRASS(.57, .67, .89),
        SOFT(0, .45, .64),
        OBSTACLE(0, 0, 0);

        private final double skipRetention;
        private final double slideDrag;
        private final double rollDrag;

        Surface(double skipRetention, double slideDrag, double rollDrag) {
            this.skipRetention = skipRetention;
            this.slideDrag = slideDrag;
            this.rollDrag = rollDrag;
        }
    }

    private final boolean putting;
    private final int rollerLevel;
    private final double rollCurve;
    private int skips;
    private boolean kicked;
    private boolean rolled;
    private Motion motion = Motion.AIR;
    private int groundTicks;

    public GroundPhysics(boolean putting, int rollerLevel, double rollCurve) {
        this.putting = putting;
        this.rollerLevel = rollerLevel;
        this.rollCurve = rollCurve;
    }

    Motion motion() { return motion; }

    Impact collide(FlightHit hit, Vector velocity) {
        if (motion != Motion.AIR || hit.face() == null) return Impact.NONE;
        double speed = horizontalSpeed(velocity);
        if (hit.face() != BlockFace.UP) {
            if (putting || kicked || hit.face() == BlockFace.DOWN || speed < .38 || !isTrunk(hit.material()))
                return Impact.NONE;
            Vector normal = hit.face().getDirection();
            double intoFace = velocity.dot(normal);
            if (intoFace >= -.08) return Impact.NONE;
            velocity.subtract(normal.multiply(2 * intoFace)).multiply(.31);
            velocity.setY(Math.clamp(Math.abs(velocity.getY()), .045, .14));
            kicked = true;
            return Impact.TREE_KICK;
        }

        Surface surface = surface(hit.material());
        if (surface == Surface.OBSTACLE || speed < .20 || velocity.getY() >= 0) return Impact.NONE;
        double slope = -velocity.getY() / speed;
        if (rollerLevel > 0 && !rolled && speed >= .5 && slope >= .12 && slope <= 1.15) {
            velocity.multiply(.75 + .025 * Math.min(4, rollerLevel - 1)).setY(0);
            motion = Motion.ROLL;
            rolled = true;
            return Impact.ROLL;
        }
        if (!putting && rollerLevel == 0 && surface.skipRetention > 0 && skips < 2
                && speed >= .78 && slope <= (surface == Surface.HARD ? .34 : .22)) {
            double lift = Math.clamp(-velocity.getY() * .55 + .045, .075, .22);
            velocity.multiply(surface.skipRetention).setY(lift);
            skips++;
            return Impact.SKIP;
        }
        velocity.multiply((putting ? .35 : .56) * surface.slideDrag).setY(0);
        if (horizontalSpeed(velocity) < .09) return Impact.NONE;
        motion = Motion.SLIDE;
        return Impact.SLIDE;
    }

    boolean crossSurface(Material material, Vector velocity) {
        Surface surface = surface(material);
        boolean rolling = motion == Motion.ROLL;
        if (rolling) velocity.rotateAroundY(rollCurve);
        velocity.multiply(rolling ? surface.rollDrag : surface.slideDrag);
        return ++groundTicks < (rolling ? 32 : 16) && horizontalSpeed(velocity) >= .09;
    }

    void fall(Vector velocity) {
        motion = Motion.AIR;
        velocity.setY(-.09);
    }

    private static double horizontalSpeed(Vector velocity) {
        return Math.hypot(velocity.getX(), velocity.getZ());
    }

    private static boolean isTrunk(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE");
    }

    private static Surface surface(Material material) {
        return switch (material) {
            case STONE, SMOOTH_STONE, COBBLESTONE, ANDESITE, GRANITE, DIORITE, STONE_BRICKS,
                    SMOOTH_STONE_SLAB, STONE_SLAB, STONE_BRICK_SLAB -> Surface.HARD;
            case GRASS_BLOCK, DIRT, COARSE_DIRT, GRAVEL, ROOTED_DIRT, DIRT_PATH -> Surface.GRASS;
            case SAND, RED_SAND, MOSS_BLOCK, PODZOL, MUD, CLAY, SOUL_SAND -> Surface.SOFT;
            default -> Surface.OBSTACLE;
        };
    }
}
