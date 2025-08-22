package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.api.round.settings.RoundSettings;
import normalmanv2.normalDiscGolf.api.round.settings.RoundType;
import normalmanv2.normalDiscGolf.api.round.delegate.settings.DelegateRoundSettings;
import normalmanv2.normalDiscGolf.common.division.Division;

public class DefaultRoundSettings implements DelegateRoundSettings {

    private final int maxTeams;
    private final RoundType type;
    private final Division division;
    private final boolean isPrivate;

    public DefaultRoundSettings(int maxTeams, RoundType type, Division division, boolean isPrivate) {
        this.maxTeams = maxTeams;
        this.type = type;
        this.division = division;
        this.isPrivate = isPrivate;
    }

    @Override
    public RoundSettings getSettings() {
        return this;
    }

    @Override
    public int maxTeams() {
        return this.maxTeams;
    }

    @Override
    public RoundType getType() {
        return this.type;
    }

    @Override
    public Division getDivision() {
        return this.division;
    }

    @Override
    public boolean isPrivate() {
        return this.isPrivate;
    }
}
