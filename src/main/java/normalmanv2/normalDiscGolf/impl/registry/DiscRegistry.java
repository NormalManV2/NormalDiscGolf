package normalmanv2.normalDiscGolf.impl.registry;

import normalmanv2.normalDiscGolf.common.disc.DiscImpl;

import java.util.HashMap;
import java.util.Map;

public class DiscRegistry {
    private final Map<String, DiscImpl> discs;

    public DiscRegistry() {
        this.discs = new HashMap<>();
    }

    public void registerDisc(String discName, DiscImpl discImpl) {
        this.discs.put(discName, discImpl);
    }

    public DiscImpl getDiscByString(String discName) throws CloneNotSupportedException {
        return this.discs.get(discName).clone();
    }
}
