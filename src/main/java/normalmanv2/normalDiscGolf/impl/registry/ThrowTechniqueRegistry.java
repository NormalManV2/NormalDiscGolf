package normalmanv2.normalDiscGolf.impl.registry;

import normalmanv2.normalDiscGolf.impl.technique.ThrowTechnique;
import normalmanv2.normalDiscGolf.impl.technique.type.BackhandTechnique;
import normalmanv2.normalDiscGolf.impl.technique.type.ForehandTechnique;
import org.normal.impl.FreezableRegistryImpl;

import java.util.HashMap;
import java.util.Map;

public class ThrowTechniqueRegistry extends FreezableRegistryImpl<String, ThrowTechnique> {


    public ThrowTechniqueRegistry() {
        super();
        this.registerDefaults();
    }

    private void registerDefaults() {
        BackhandTechnique backhand = new BackhandTechnique();
        ForehandTechnique forehand = new ForehandTechnique();

        this.register(backhand.getId(), backhand);
        this.register(forehand.getId(), forehand);
    }
}
