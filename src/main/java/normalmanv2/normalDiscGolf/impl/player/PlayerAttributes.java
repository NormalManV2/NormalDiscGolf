package normalmanv2.normalDiscGolf.impl.player;

/**
 * Represents attributes that a player can have, that can be modified
 */
public class PlayerAttributes {

    private int accuracy;
    private int form;
    private int power;

    public PlayerAttributes(int accuracy, int form, int power) {
        this.accuracy = accuracy;
        this.form = form;
        this.power = power;
    }

    public PlayerAttributes() {
        this(1, 1, 1);
    }

    /**
     * Gets the accuracy value
     *
     * @return the accuracy value no less than 1
     */
    public int getAccuracy() {
        return accuracy;
    }

    /**
     * Gets the form value
     *
     * @return the form value no less than 1
     */
    public int getForm() {
        return form;
    }

    /**
     * Gets the power value
     *
     * @return the power value no less than 1
     */
    public int getPower() {
        return power;
    }

    /**
     * Increments the current accuracy
     *
     * @param amount amount to increment by
     */
    public void incrementAccuracy(int amount) {
        this.accuracy += amount;
    }

    /**
     * Increments the current form
     *
     * @param amount amount to increment by
     */
    public void incrementForm(int amount) {
        this.form += amount;
    }

    /**
     * Increments the current power
     *
     * @param amount amount to increment by
     */
    public void incrementPower(int amount) {
        this.power += power;
    }
}
