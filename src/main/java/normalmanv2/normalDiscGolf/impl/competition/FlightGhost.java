package normalmanv2.normalDiscGolf.impl.competition;

import java.util.List;

/** A complete scorecard with sampled throws independent of its round world. */
public record FlightGhost(List<Hole> holes) {
    public static final int MAX_POINTS_PER_THROW = 32;
    private static final double POSITION_SCALE = 16;

    public FlightGhost {
        holes = List.copyOf(holes);
        if (holes.isEmpty() || holes.size() > 18) throw new IllegalArgumentException("Invalid ghost hole count");
    }

    public long totalStrokes() { return holes.stream().mapToLong(Hole::strokes).sum(); }

    public record Hole(int strokes, List<List<Point>> throwsInOrder) {
        public Hole {
            throwsInOrder = throwsInOrder.stream().map(List::copyOf).toList();
            if (strokes < 1 || throwsInOrder.isEmpty() || throwsInOrder.size() > strokes
                    || throwsInOrder.stream().anyMatch(path -> path.size() < 2 || path.size() > MAX_POINTS_PER_THROW))
                throw new IllegalArgumentException("Invalid ghost hole");
        }
    }

    public record Point(short x16, short y16, short z16) {
        public static Point at(double x, double y, double z) {
            return new Point(quantize(x), quantize(y), quantize(z));
        }

        private static short quantize(double coordinate) {
            long scaled = Math.round(coordinate * POSITION_SCALE);
            if (!Double.isFinite(coordinate) || scaled < Short.MIN_VALUE || scaled > Short.MAX_VALUE)
                throw new IllegalArgumentException("Position outside ghost recording range");
            return (short) scaled;
        }

        public double x() { return x16 / POSITION_SCALE; }
        public double y() { return y16 / POSITION_SCALE; }
        public double z() { return z16 / POSITION_SCALE; }
    }
}
