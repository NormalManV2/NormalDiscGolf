package normalmanv2.normalDiscGolf.impl.registry;

import normalmanv2.normalDiscGolf.impl.technique.ThrowTechnique;
import normalmanv2.normalDiscGolf.impl.technique.type.BackhandTechnique;
import normalmanv2.normalDiscGolf.impl.technique.type.ForehandTechnique;

public class ThrowTechniqueRegistry extends GameRegistry<String, ThrowTechnique> {

    public ThrowTechniqueRegistry() {
        super();
        this.registerDefaults();
        this.freeze();
    }

    private void registerDefaults() {
        BackhandTechnique backhand = new BackhandTechnique();
        ForehandTechnique forehand = new ForehandTechnique();

        this.register(backhand.getId(), backhand);
        this.register(forehand.getId(), forehand);
    }
}
