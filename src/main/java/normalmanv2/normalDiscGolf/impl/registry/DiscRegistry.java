package normalmanv2.normalDiscGolf.impl.registry;

import normalmanv2.normalDiscGolf.common.disc.DiscImpl;

public class DiscRegistry extends GameRegistry<String, DiscImpl> {

    public DiscImpl getDiscByName(String discName) throws CloneNotSupportedException {

        for (String key : this.getBackingMap().keySet()) {
            if (key.equalsIgnoreCase(discName)) {
                return this.getBackingMap().get(key).clone();
            }
        }
        throw new RuntimeException("Disc not found! " + discName);
    }
}
