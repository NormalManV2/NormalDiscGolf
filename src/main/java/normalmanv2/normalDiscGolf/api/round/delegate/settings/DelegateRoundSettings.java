package normalmanv2.normalDiscGolf.api.round.delegate.settings;

import normalmanv2.normalDiscGolf.api.round.settings.RoundSettings;
import normalmanv2.normalDiscGolf.api.round.settings.RoundType;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;

public interface DelegateRoundSettings extends RoundSettings {
    RoundSettings getSettings();

    @Override
    default int maxTeams() {
        return this.getSettings().maxTeams();
    }

    @Override
    default RoundType type() {
        return this.getSettings().type();
    }

    @Override
    default Division division() {
        return this.getSettings().division();
    }

    @Override
    default boolean isPrivate() {
        return this.getSettings().isPrivate();
    }

    static RoundSettings create(int maxTeams, RoundType type, Division division, boolean isPrivate) {
        return new DefaultRoundSettings(maxTeams, type, division, isPrivate);
    }
}
