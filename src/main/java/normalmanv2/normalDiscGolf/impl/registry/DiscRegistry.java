package normalmanv2.normalDiscGolf.impl.registry;

import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import org.normal.impl.registry.RegistryImpl;

public class DiscRegistry extends RegistryImpl<String, DiscImpl> {

    public DiscImpl getDiscByString(String discName) throws CloneNotSupportedException {
        return this.get(discName).clone();
    }
}
