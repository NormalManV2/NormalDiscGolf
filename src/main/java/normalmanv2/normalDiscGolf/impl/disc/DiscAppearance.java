package normalmanv2.normalDiscGolf.impl.disc;

public record DiscAppearance(Shape shape, int plate, int rim, int accent) {
    public enum Shape {
        CLASSIC, ROUNDED, DOME, PUDDLE, BEVELED, LOW_PROFILE
    }
}
