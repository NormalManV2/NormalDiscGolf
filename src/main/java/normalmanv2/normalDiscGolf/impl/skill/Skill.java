package normalmanv2.normalDiscGolf.impl.skill;

public class Skill {

    private int level;
    private final int maxLevel;

    private double xp;
    private double xpToNext;

    public Skill(int startingLevel, int maxLevel) {
        this.level = startingLevel;
        this.maxLevel = maxLevel;
        this.xp = 0;
        this.xpToNext = this.computeXpNeeded(this.level);
    }

    public int getLevel() {
        return this.level;
    }
       public int getMaxLevel() {
        return this.maxLevel;
    }

    public double getXp() {
        return this.xp;
    }

    protected void incrLevel() {
        this.level++;
        this.onLevelUp();
    }

    public void addXp(double amount) {
        if (this.level >= this.maxLevel) return;

        this.xp += amount;
        while (this.xp >= this.xpToNext && level < this.maxLevel) {
            this.xp -= this.xpToNext;
            this.incrLevel();
            this.xpToNext = this.computeXpNeeded(this.level);
        }
    }

    protected double computeXpNeeded(int currentLevel) {
        return 50 + (currentLevel * currentLevel * 15);
    }

    protected void onLevelUp() {

    }

}
