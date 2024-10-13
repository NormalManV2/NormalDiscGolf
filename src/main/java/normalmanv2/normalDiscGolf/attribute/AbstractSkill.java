package normalmanv2.normalDiscGolf.attribute;

public class AbstractSkill implements Skill {

    private int level;
    private int maxLevel;

    public AbstractSkill(int level, int maxLevel) {
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
