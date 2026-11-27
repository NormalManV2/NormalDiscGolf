package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.disc.ShotSetup.Angle;
import normalmanv2.normalDiscGolf.impl.disc.ShotSetup.Stance;
import org.bukkit.util.Vector;

/** Air motion for shaped shots. Release directions and bending rates are shared with the aiming preview. */
public final class TrickFlight {
    private final Stance stance;
    private final double bendSign;
    private final double drag;
    private final double glide;
    private final double turn;
    private final double fade;
    private final int flexBreak;
    private final double panStrength;

    public TrickFlight(Stance stance, DiscImpl disc, Angle angle, boolean mirrored, double drag) {
        this.stance = stance;
        this.bendSign = mirrored ? -1 : 1;
        this.drag = drag;
        this.glide = disc.getGlide();
        this.turn = Math.max(0, -disc.getTurn());
        this.fade = disc.getFade();
        this.flexBreak = 9 - 2 * angle.bank();
        this.panStrength = 1 + .15 * angle.bank();
    }

    public Vector aim(Vector direction) {
        double rise = switch (stance) {
            case FLEX -> Math.max(.14, direction.getY());
            case TOMAHAWK -> Math.clamp(direction.getY(), .64, .91);
            case THUMBER -> Math.clamp(direction.getY(), .73, .94);
            default -> throw new IllegalStateException("Unsupported trick shot: " + stance);
        };
        double heading = Math.atan2(direction.getZ(), direction.getX());
        double forward = Math.sqrt(1 - rise * rise);
        return new Vector(Math.cos(heading) * forward, rise, Math.sin(heading) * forward);
    }

    public double speedScale() {
        return switch (stance) {
            case FLEX -> .96;
            case TOMAHAWK -> .88;
            case THUMBER -> .78;
            default -> throw new IllegalStateException("Unsupported trick shot: " + stance);
        };
    }

    public void advance(Vector motion, int tick) {
        if (stance == Stance.FLEX) {
            motion.multiply(drag);
            if (tick > 3) motion.setY(motion.getY() - (.031 - .0015 * glide));
            // The forced anhyzer holds first. Fade then brings the disc back across its release heading.
            double bend = tick <= flexBreak
                    ? -(.034 + .004 * turn)
                    : (.055 + .005 * fade) * Math.min(1, (tick - flexBreak) / 4.0)
                            * Math.clamp((39 - tick) / 14.0, .2, 1);
            motion.rotateAroundY(bendSign * bend);
            return;
        }

        boolean thumber = stance == Stance.THUMBER;
        motion.multiply(drag * .982);
        motion.setY(motion.getY() - (thumber ? .086 : .078));
        if (motion.getY() < 0) motion.setX(motion.getX() * .9).setZ(motion.getZ() * .9);
        // Both start nearly straight, then pan in opposite directions near the top of the arc.
        double pan = Math.max(0, 1 - Math.abs(tick - 11) / 9.0);
        motion.rotateAroundY(bendSign * (thumber ? -1 : 1) * panStrength * (.053 + .003 * fade) * pan);
    }

    public static double releaseControl(int level) {
        return 1 - .09 * Math.clamp(level - 1, 0, 4);
    }
}
