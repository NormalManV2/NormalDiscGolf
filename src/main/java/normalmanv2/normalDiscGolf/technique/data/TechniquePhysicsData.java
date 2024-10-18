package normalmanv2.normalDiscGolf.technique.data;

public class TechniquePhysicsData {
    private final double flightPhase;
    private final double turnFactor;
    private final double fadeFactor;

    public TechniquePhysicsData(double flightPhase, double turnFactor, double fadeFactor) {
        this.flightPhase = flightPhase;
        this.turnFactor = turnFactor;
        this.fadeFactor = fadeFactor;
    }

    public double getFlightPhase() {
        return this.flightPhase;
    }

    public double getTurnFactor() {
        return this.turnFactor;
    }

    public double getFadeFactor() {
        return this.fadeFactor;
    }
}
