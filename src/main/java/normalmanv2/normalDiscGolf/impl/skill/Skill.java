package normalmanv2.normalDiscGolf.impl.skill;

public class Skill implements ISkill {

    private int level;
    private int maxLevel;

    public Skill(int level, int maxLevel) {
        this.level = level;
        this.maxLevel = maxLevel;
    }

    @Override
    public int getLevel() {
        return this.level;
    }

    @Override
    public int getMaxLevel() {
        return this.maxLevel;
    }

    @Override
    public void setLevel(int level) {
        this.level = level;
    }

    @Override
    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }
}
