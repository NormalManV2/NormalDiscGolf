package normalmanv2.normalDiscGolf.impl.player;

import normalmanv2.normalDiscGolf.impl.player.score.PDGARating;

public class PlayerData {

    private final PlayerSkills skills;
    private final PDGARating rating;
    private DiscBag discBag = new DiscBag();
    private DecalLocker decalLocker = new DecalLocker();

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

}
