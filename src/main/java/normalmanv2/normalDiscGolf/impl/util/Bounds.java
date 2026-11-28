package normalmanv2.normalDiscGolf.impl.util;

public final class Bounds {
    public static final double TAU = 2 * Math.PI;

    private Bounds() {}

    public static int clamp(int value, int min, int max) {
        if (min > max) throw new IllegalArgumentException("Minimum exceeds maximum");
        return Math.max(min, Math.min(value, max));
    }

    public static double clamp(double value, double min, double max) {
        if (Double.compare(min, max) > 0 || Double.isNaN(min) || Double.isNaN(max))
            throw new IllegalArgumentException("Invalid bounds");
        return Math.max(min, Math.min(value, max));
    }

    public static float clamp(float value, float min, float max) {
        return (float) clamp((double) value, min, max);
    }

    public static int ceilDiv(int dividend, int divisor) {
        int quotient = dividend / divisor;
        return (dividend ^ divisor) >= 0 && dividend % divisor != 0 ? quotient + 1 : quotient;
    }
}
