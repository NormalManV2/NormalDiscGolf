package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.impl.disc.Disc;

import java.util.HashMap;
import java.util.Map;

public class DiscRegistry {
    private final Map<String, Disc> discs;

    public DiscRegistry() {
        this.discs = new HashMap<>();
    }

    public void registerDisc(String discName, Disc disc) {
        this.discs.put(discName, disc);
    }

    public Disc getDiscByString(String discName) throws CloneNotSupportedException {
        return this.discs.get(discName).clone();
    }
}
