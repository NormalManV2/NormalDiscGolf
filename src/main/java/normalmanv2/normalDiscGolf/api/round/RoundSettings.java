package normalmanv2.normalDiscGolf.api.round;

import normalmanv2.normalDiscGolf.common.division.Division;

public interface RoundSettings {

    int maxTeams();

    RoundType getType();

    Division getDivision();

    void setDivision(Division division);

    boolean isPrivate();
}
