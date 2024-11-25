package normalmanv2.normalDiscGolf.impl.event;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DiscThrowEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final DiscImpl thrownDiscImpl;
    private final CourseImpl currentCourseImpl;
    private final int holeNumber;
    private final Player player;
    private final GameRound round;
    private final String technique;

    public DiscThrowEvent(DiscImpl thrownDiscImpl, CourseImpl currentCourseImpl, int holeNumber, Player player, GameRound round, String technique) {
        this.thrownDiscImpl = thrownDiscImpl;
        this.currentCourseImpl = currentCourseImpl;
        this.holeNumber = holeNumber;
        this.player = player;
        this.round = round;
        this.technique = technique;
    }

    public DiscImpl getThrownDisc() {
        return this.thrownDiscImpl;
    }

    public CourseImpl getCurrentCourse() {
        return this.currentCourseImpl;
    }

    public int getHoleNumber() {
        return this.holeNumber;
    }

    public Player getPlayer() {
        return this.player;
    }

    public GameRound getRound() {
        return this.round;
    }

    public String getTechnique() {
        return this.technique;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

}
