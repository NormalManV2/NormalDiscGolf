package normalmanv2.normalDiscGolf.impl.event;

import normalmanv2.normalDiscGolf.impl.course.Course;
import normalmanv2.normalDiscGolf.impl.disc.Disc;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DiscThrowEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Disc thrownDisc;
    private final Course currentCourse;
    private final int holeNumber;
    private final Player player;

    public DiscThrowEvent(Disc thrownDisc, Course currentCourse, int holeNumber, Player player) {
        this.thrownDisc = thrownDisc;
        this.currentCourse = currentCourse;
        this.holeNumber = holeNumber;
        this.player = player;
    }

    public Disc getThrownDisc() {
        return this.thrownDisc;
    }

    public Course getCurrentCourse() {
        return this.currentCourse;
    }

    public int getHoleNumber() {
        return this.holeNumber;
    }

    public Player getPlayer() {
        return this.player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

}
