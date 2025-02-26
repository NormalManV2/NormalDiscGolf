package normalmanv2.normalDiscGolf.impl.registry;

import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import org.normal.impl.registry.RegistryImpl;

public class DiscRegistry extends RegistryImpl<String, DiscImpl> {

    public DiscImpl getDiscByName(String discName) throws CloneNotSupportedException {

        for (String key : this.getBackingMap().keySet()) {
            if (key.equalsIgnoreCase(discName)) {
                return this.getBackingMap().get(key).clone();
            }
        }
        throw new RuntimeException("Disc not found! " + discName);
    }
}
