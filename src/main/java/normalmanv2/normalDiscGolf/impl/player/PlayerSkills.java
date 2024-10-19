package normalmanv2.normalDiscGolf.impl.player;

import normalmanv2.normalDiscGolf.impl.skill.*;

public class PlayerSkills {

    private final Accuracy accuracy;
    private final Backhand backhand;
    private final Flex flex;
    private final Forehand forehand;
    private final Form form;
    private final Power power;
    private final Roller roller;
    private final SCurve sCurve;
    private final Thumber thumber;
    private final Tomahawk tomahawk;

    public PlayerSkills(int startingLevel, int maxLevel) {
        this.accuracy = new Accuracy(startingLevel, maxLevel);
        this.backhand = new Backhand(startingLevel, maxLevel);
        this.flex = new Flex(startingLevel, maxLevel);
        this.forehand = new Forehand(startingLevel, maxLevel);
        this.form = new Form(startingLevel, maxLevel);
        this.power = new Power(startingLevel, maxLevel);
        this.roller = new Roller(startingLevel, maxLevel);
        this.sCurve = new SCurve(startingLevel, maxLevel);
        this.thumber = new Thumber(startingLevel, maxLevel);
        this.tomahawk = new Tomahawk(startingLevel, maxLevel);
    }

    public Accuracy getAccuracy() {
        return this.accuracy;
    }

    public Backhand getBackhand() {
        return this.backhand;
    }

    public Flex getFlex() {
        return this.flex;
    }

    public Forehand getForehand() {
        return this.forehand;
    }

    public Form getForm() {
        return this.form;
    }

    public Power getPower() {
        return this.power;
    }

    public Roller getRoller() {
        return this.roller;
    }

    public SCurve getSCurve() {
        return this.sCurve;
    }

    public Thumber getThumber() {
        return this.thumber;
    }

    public Tomahawk getTomahawk() {
        return this.tomahawk;
    }
}
