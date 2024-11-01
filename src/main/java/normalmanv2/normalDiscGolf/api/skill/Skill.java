package normalmanv2.normalDiscGolf.api.skill;

public class Skill {

    private int level;
    private int maxLevel;

    public Skill(int level, int maxLevel) {
        this.level = level;
        this.maxLevel = maxLevel;
    }

    public int getLevel() {
        return this.level;
    }


    public int getMaxLevel() {
        return this.maxLevel;
    }


    public void setLevel(int level) {
        this.level = level;
    }


    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }
}
