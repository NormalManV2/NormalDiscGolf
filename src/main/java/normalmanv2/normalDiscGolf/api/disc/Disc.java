package normalmanv2.normalDiscGolf.api.disc;

public interface Disc extends Cloneable {

    int getSpeed();
    int getGlide();
    double getTurn();
    double getFade();
    String getName();
    DiscType getType();
}
