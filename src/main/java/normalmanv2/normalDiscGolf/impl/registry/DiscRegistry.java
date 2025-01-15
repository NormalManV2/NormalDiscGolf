package normalmanv2.normalDiscGolf.impl.registry;

import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import org.normal.impl.FreezableRegistryImpl;
import org.normal.impl.RegistryImpl;

import java.util.HashMap;
import java.util.Map;

public class DiscRegistry extends FreezableRegistryImpl<String, DiscImpl> {

    public DiscImpl getDiscByString(String discName) throws CloneNotSupportedException {
        return this.get(discName).clone();
    }
}
