package normalmanv2.normalDiscGolf.impl.player;

import normalmanv2.normalDiscGolf.impl.player.score.PDGARating;
import normalmanv2.normalDiscGolf.impl.player.score.RoundSummary;
import normalmanv2.normalDiscGolf.impl.player.clubhouse.ClubhouseProgress;

import java.util.Set;

public class PlayerData {

    private final PlayerSkills skills;
    private final PDGARating rating;
    private DiscBag discBag = new DiscBag();
    private DecalLocker decalLocker = new DecalLocker();
    private Set<String> unavailableDiscIds = Set.of();
    private ClubhouseProgress clubhouse = new ClubhouseProgress();
    private RoundSummary latestRoundSummary;
    private Set<String> competitionRewards = Set.of();

    public PlayerData() {
        this.skills = new PlayerSkills(1, 5);
        this.rating = new PDGARating();
    }

    public PlayerSkills getSkills() {
        return this.skills;
    }

    public PDGARating getRating() {
        return this.rating;
    }

    public DiscBag getDiscBag() { return discBag; }
    public void setDiscBag(DiscBag discBag) { this.discBag = discBag; }
    public DecalLocker getDecalLocker() { return decalLocker; }
    public void setDecalLocker(DecalLocker decalLocker) { this.decalLocker = decalLocker; }
    public Set<String> getUnavailableDiscIds() { return unavailableDiscIds; }
    public void setUnavailableDiscIds(Set<String> ids) { unavailableDiscIds = Set.copyOf(ids); }
    public ClubhouseProgress getClubhouse() { return clubhouse; }
    public void setClubhouse(ClubhouseProgress clubhouse) { this.clubhouse = clubhouse; }

    public RoundSummary getLatestRoundSummary() { return latestRoundSummary; }
    public void setLatestRoundSummary(RoundSummary summary) { this.latestRoundSummary = summary; }
    public Set<String> getCompetitionRewards() { return competitionRewards; }
    public void setCompetitionRewards(Set<String> receipts) { competitionRewards = Set.copyOf(receipts); }

}
