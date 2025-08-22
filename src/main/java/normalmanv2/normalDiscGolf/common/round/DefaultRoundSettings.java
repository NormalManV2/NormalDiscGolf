package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.api.round.settings.RoundSettings;
import normalmanv2.normalDiscGolf.api.round.settings.RoundType;
import normalmanv2.normalDiscGolf.api.round.delegate.settings.DelegateRoundSettings;
import normalmanv2.normalDiscGolf.common.division.Division;

public record DefaultRoundSettings(
        int maxTeams,
        RoundType type,
        Division division,
        boolean isPrivate) implements DelegateRoundSettings {

    @Override
    public RoundSettings getSettings() {
        return this;
    }
}
