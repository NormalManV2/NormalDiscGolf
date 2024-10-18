package normalmanv2.normalDiscGolf.technique;

import normalmanv2.normalDiscGolf.technique.type.BackhandTechnique;
import normalmanv2.normalDiscGolf.technique.type.ForehandTechnique;

import java.util.HashMap;
import java.util.Map;

public class ThrowTechniqueRegistry {
    private final Map<String, ThrowTechnique> techniques;

    public ThrowTechniqueRegistry() {
        this.techniques = new HashMap<>();

        this.registerDefaults();
    }

    public ThrowTechnique getTechnique(String technique) {
        return this.techniques.get(technique);
    }

    public void registerTechnique(ThrowTechnique technique) {
        this.techniques.put(technique.getTechnique(), technique);
    }

    private void registerDefaults() {
        this.registerTechnique(new BackhandTechnique());
        this.registerTechnique(new ForehandTechnique());
    }
}
