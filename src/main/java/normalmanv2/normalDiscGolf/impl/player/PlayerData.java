package normalmanv2.normalDiscGolf.impl.player;

import normalmanv2.normalDiscGolf.impl.player.score.PDGARating;

public class PlayerData {

    private final PlayerAttributes attributes;
    private final PDGARating rating;

    public PlayerData() {
        this.attributes = new PlayerAttributes();
        this.rating = new PDGARating();
    }

    public PlayerAttributes getAttributes() {
        return attributes;
    }

    public PDGARating getRating() {
        return this.rating;
    }



}
