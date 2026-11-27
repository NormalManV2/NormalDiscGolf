package normalmanv2.normalDiscGolf.impl.course.event;

import java.util.Locale;

public enum CourseEvent {
    CLEAR("clear", "Clear"),
    THUNDERSTORM("thunderstorm", "Thunderstorm"),
    FOG("fog", "Fog"),
    NIGHT_GOLF("night-golf", "Night Golf");

    private final String id;
    private final String label;

    CourseEvent(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String status() { return "Course event: " + label; }

    static CourseEvent parse(String text) {
        String id = text.trim().toLowerCase(Locale.ROOT);
        for (CourseEvent event : values()) if (event.id.equals(id)) return event;
        return null;
    }
}
