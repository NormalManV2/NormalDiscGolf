package normalmanv2.normalDiscGolf.api.round.delegate.settings;

import normalmanv2.normalDiscGolf.api.round.settings.RoundSettings;
import normalmanv2.normalDiscGolf.api.round.settings.RoundType;
import normalmanv2.normalDiscGolf.common.division.Division;

public interface DelegateRoundSettings extends RoundSettings {
    RoundSettings getSettings();

    @Override
    default int maxTeams() {
        return this.getSettings().maxTeams();
    }

    @Override
    default RoundType getType() {
        return this.getSettings().getType();
    }

    @Override
    default Division getDivision() {
        return this.getSettings().getDivision();
    }

    @Override
    default boolean isPrivate() {
        return this.getSettings().isPrivate();
    }

}
