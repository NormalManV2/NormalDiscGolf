package normalmanv2.normalDiscGolf.attribute;

public class AbstractSkill implements Skill {
//
    private int level;
    private int maxLevel;

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
