package normalmanv2.normalDiscGolf.api.round.settings;

import normalmanv2.normalDiscGolf.common.division.Division;

public interface RoundSettings {

    int maxTeams();

    RoundType type();

    Division division();

    boolean isPrivate();
}
