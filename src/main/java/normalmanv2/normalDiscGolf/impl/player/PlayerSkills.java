package normalmanv2.normalDiscGolf.impl.player;

import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.impl.skill.*;

import java.util.ArrayList;
import java.util.List;

public class PlayerSkills {

    public record Gain(String skill, int xp, int level, boolean leveledUp) {}

    public List<Gain> practice(DiscType disc, String technique, boolean hazard, boolean goal) {
        List<Gain> gains = new ArrayList<>();
        award(gains, technique, technique(technique), hazard ? 1 : goal ? 10 : 5);
        award(gains, "form", form, hazard ? 1 : goal ? 6 : 3);
        if (!hazard) {
            award(gains, "accuracy", accuracy, goal ? 10 : 3);
            int powerXp = switch (disc) {
                case DRIVER -> 5;
                case MID_RANGE -> 3;
                default -> 1;
            };
            award(gains, "power", power, powerXp);
        }
        return gains;
    }

    private static void award(List<Gain> gains, String name, Skill skill, int xp) {
        if (skill == null || skill.getLevel() >= skill.getMaxLevel()) return;
        int before = skill.getLevel();
        skill.addXp(xp);
        gains.add(new Gain(name, xp, skill.getLevel(), skill.getLevel() > before));
    }

    public double launchSpeedMultiplier() {
        return 1.07 + .05 * (power.getLevel() - 1);
    }

    public double releaseSpreadDegrees() {
        return 1.2 - .25 * (accuracy.getLevel() - 1);
    }

    public double flightDrag() {
        return .98 + .002 * (form.getLevel() - 1);
    }

    public double fadeMultiplier(String id) {
        Skill skill = technique(id);
        return skill == null ? 1 : 1 - .08 * (skill.getLevel() - 1);
    }

    private Skill technique(String id) {
        return switch (id) {
            case "backhand" -> backhand;
            case "forehand" -> forehand;
            default -> null;
        };
    }

    public java.util.Map<String, Skill> asMap() {
        return java.util.Map.of("accuracy", accuracy, "backhand", backhand, "flex", flex,
                "forehand", forehand, "form", form, "power", power, "roller", roller,
                "s-curve", sCurve, "thumber", thumber, "tomahawk", tomahawk);
    }

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
