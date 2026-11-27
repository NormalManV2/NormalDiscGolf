package normalmanv2.normalDiscGolf.impl.round;

import org.bukkit.Location;
import java.util.*;

/** Distance-paced, rounded fairway path with a bounded forward-looking camera. */
public final class HolePreviewPath {
    private final List<Location> samples = new ArrayList<>();
    private final List<Double> distances = new ArrayList<>();
    private double length;
    public HolePreviewPath(List<Location> source) {
        List<Location> points = new ArrayList<>();
        for (Location point : source)
            if (points.isEmpty() || points.getLast().toVector().distanceSquared(point.toVector()) > .25) points.add(point.clone());
        if (points.size() < 2) throw new IllegalArgumentException("A preview needs two distinct points");
        for (int segment = 0; segment < points.size() - 1; segment++) {
            Location a = points.get(Math.max(0, segment - 1)), b = points.get(segment);
            Location c = points.get(segment + 1), d = points.get(Math.min(points.size() - 1, segment + 2));
            for (int step = 0; step < 16; step++) {
                double t = step / 16.0;
                add(new Location(b.getWorld(), curve(a.getX(),b.getX(),c.getX(),d.getX(),t),
                        curve(a.getY(),b.getY(),c.getY(),d.getY(),t),
                        curve(a.getZ(),b.getZ(),c.getZ(),d.getZ(),t)));
            }
        }
        add(points.getLast().clone());
    }
    private static double curve(double a,double b,double c,double d,double t) {
        return .5*((2*b)+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t);
    }
    private void add(Location point) {
        if (!samples.isEmpty()) length += samples.getLast().toVector().distance(point.toVector());
        samples.add(point); distances.add(length);
    }
    private Location at(double distance) {
        distance = Math.clamp(distance, 0, length);
        int index = Collections.binarySearch(distances, distance);
        if (index >= 0) return samples.get(index).clone();
        int upper = Math.min(samples.size()-1, -index-1), lower = Math.max(0,upper-1);
        double span = distances.get(upper)-distances.get(lower);
        double t = span == 0 ? 0 : (distance-distances.get(lower))/span;
        return samples.get(lower).clone().add(samples.get(upper).toVector().subtract(samples.get(lower).toVector()).multiply(t));
    }
    public Location frame(double progress) {
        return movingFrame(progress, false, null);
    }

    Location previewFrame(double progress, boolean woodland, Location hazard) {
        // Finish the move early enough to give the basket a brief, steady frame.
        return movingFrame(Math.min(1, progress / .87), woodland, hazard);
    }

    private Location movingFrame(double progress, boolean woodland, Location hazard) {
        double eased = .5 - .5 * Math.cos(Math.PI * Math.clamp(progress, 0, 1));
        int behindTee = woodland ? 6 : 0;
        double distance = Math.max(0, length - (woodland ? 14 : 16)) * eased - behindTee * (1 - eased);
        Location camera = at(distance);
        if (distance < 0) {
            var forward = at(8).toVector().subtract(at(0).toVector()).setY(0).normalize();
            camera.add(forward.multiply(distance));
        }
        camera.add(0, woodland ? 16 - 5 * eased : 11, 0);
        Location focus = at(distance + (woodland ? 24 : 20)).add(0, 1.5, 0);
        if (hazard != null) {
            double proximity = 1 - Math.clamp((focus.distance(hazard) - 5) / 24, 0, 1);
            double strength = .55 * proximity * proximity * (1 - Math.clamp((progress - .68) / .22, 0, 1));
            focus.add(hazard.toVector().subtract(focus.toVector()).multiply(strength));
        }
        camera.setDirection(focus.toVector().subtract(camera.toVector()));
        camera.setPitch(Math.clamp(camera.getPitch(), -10f, 35f));
        return camera;
    }
}
