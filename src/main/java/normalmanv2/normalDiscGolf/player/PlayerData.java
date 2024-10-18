package normalmanv2.normalDiscGolf.player;

import normalmanv2.normalDiscGolf.player.score.PDGARating;

public class PlayerData {

    private final PlayerSkills skills;
    private final PDGARating rating;

    public PlayerData() {
        this.skills = new PlayerSkills(0, 5);
        this.rating = new PDGARating();
    }

    public PlayerSkills getSkills() {
        return this.skills;
    }

    public PDGARating getRating() {
        return this.rating;
    }

}
