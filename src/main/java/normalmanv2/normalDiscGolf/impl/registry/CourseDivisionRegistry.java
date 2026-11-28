package normalmanv2.normalDiscGolf.impl.registry;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.difficulty.CourseDifficulty;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class CourseDivisionRegistry extends GameRegistry<Division, CourseDifficulty> {

    public CourseDivisionRegistry() {
        super();
        this.registerDefaults();
        this.freeze();
    }

    private void registerDefaults() {
        Map<Division, CourseDifficulty> defaultMapping = Arrays.stream(Division.values())
                .collect(Collectors.toMap(
                        division -> division,
                        Division::getDifficulty
                ));

        this.set(defaultMapping);
    }

}
