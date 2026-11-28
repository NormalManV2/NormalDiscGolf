package normalmanv2.normalDiscGolf.impl.player;

import normalmanv2.normalDiscGolf.impl.util.Bounds;
import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.impl.disc.ShotSetup;
import normalmanv2.normalDiscGolf.impl.disc.ShotSetup.Stance;
import normalmanv2.normalDiscGolf.impl.skill.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlayerSkills {

    public record Gain(String skill, int xp, int level, boolean leveledUp) {}
    public record PracticeContext(boolean recovery, boolean windy, boolean pressure) {
        public static final PracticeContext OPEN_LIE = new PracticeContext(false, false, false);
    }

    public List<Gain> practice(DiscType disc, String technique, boolean hazard, boolean goal) {
        return practice(disc, technique, hazard, goal, false);
    }

    public List<Gain> practice(DiscType disc, String technique, boolean hazard, boolean goal, boolean rollerShot) {
        return practice(disc, technique, hazard, goal, rollerShot ? Stance.ROLLER : Stance.NORMAL);
    }

    public List<Gain> practice(DiscType disc, String technique, boolean hazard, boolean goal, Stance stance) {
        return practice(disc, technique, hazard, goal, stance, PracticeContext.OPEN_LIE);
    }

    public List<Gain> practice(DiscType disc, String technique, boolean hazard, boolean goal, Stance stance,
                               PracticeContext context) {
        List<Gain> gains = new ArrayList<>();
        int releaseXp = hazard ? 1 : goal ? 10 : 5;
        if (!stance.isOverhand()) award(gains, technique, technique(technique), releaseXp);
        Skill special = stanceSkill(stance);
        if (special != null) award(gains, stance.id(), special, releaseXp);
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
        if (stance == Stance.PUTT) award(gains, "putting", putting, hazard ? 1 : goal ? 10 : 5);
        if (context.recovery()) award(gains, "recovery", recovery, hazard ? 2 : 6);
        if (context.windy()) award(gains, "wind-reading", windReading, hazard ? 1 : 3);
        if (context.pressure()) award(gains, "mental-focus", mentalFocus, hazard ? 1 : goal ? 8 : 4);
        return gains;
    }

    public int stanceLevel(Stance stance) {
        Skill skill = stanceSkill(stance);
        return skill == null ? 1 : skill.getLevel();
    }

    private Skill stanceSkill(Stance stance) {
        return switch (stance) {
            case ROLLER -> roller;
            case FLEX -> flex;
            case TOMAHAWK -> tomahawk;
            case THUMBER -> thumber;
            default -> null;
        };
    }

    private static void award(List<Gain> gains, String name, Skill skill, int xp) {
        if (skill == null || skill.getLevel() >= skill.getMaxLevel()) return;
        int before = skill.getLevel();
        skill.addXp(xp);
        gains.add(new Gain(name, xp, skill.getLevel(), skill.getLevel() > before));
    }

    public double launchSpeedMultiplier() {
        return 1.07 + .025 * (power.getLevel() - 1);
    }

    public double releaseSpreadDegrees() {
        return releaseSpreadDegrees(false);
    }

    public double releaseSpreadDegrees(boolean roughLie) {
        double spread = 1.2 - .17 * (accuracy.getLevel() - 1);
        return roughLie ? spread * (1 - .045 * (recovery.getLevel() - 1)) : spread;
    }

    public double timingError(double position, Stance stance, boolean pressure) {
        double bonus = stance == Stance.PUTT ? .012 * (putting.getLevel() - 1) : 0;
        if (pressure) bonus += .009 * (mentalFocus.getLevel() - 1);
        return ShotSetup.timingError(position, accuracy.getLevel(), bonus);
    }

    public double flightDrag() {
        return .98 + .002 * (form.getLevel() - 1);
    }

    public double turnMultiplier() {
        return 1 - .08 * Bounds.clamp(form.getLevel() - 1, 0, 4);
    }

    public double fadeMultiplier(String id) {
        Skill skill = technique(id);
        return skill == null ? 1 : 1 - .05 * (skill.getLevel() - 1);
    }

    private Skill technique(String id) {
        return switch (id) {
            case "backhand" -> backhand;
            case "forehand" -> forehand;
            default -> null;
        };
    }

    public Map<String, Skill> asMap() { return skills; }

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
    private final Skill putting;
    private final Skill recovery;
    private final Skill windReading;
    private final Skill mentalFocus;
    private final Map<String, Skill> skills;

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
        this.putting = new Skill(startingLevel, maxLevel);
        this.recovery = new Skill(startingLevel, maxLevel);
        this.windReading = new Skill(startingLevel, maxLevel);
        this.mentalFocus = new Skill(startingLevel, maxLevel);
        this.skills = Map.ofEntries(Map.entry("accuracy", accuracy), Map.entry("backhand", backhand), Map.entry("flex", flex),
                Map.entry("forehand", forehand), Map.entry("form", form), Map.entry("power", power), Map.entry("roller", roller),
                Map.entry("s-curve", sCurve), Map.entry("thumber", thumber), Map.entry("tomahawk", tomahawk),
                Map.entry("putting", putting), Map.entry("recovery", recovery), Map.entry("wind-reading", windReading),
                Map.entry("mental-focus", mentalFocus));
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

    public Skill getWindReading() { return windReading; }
}
