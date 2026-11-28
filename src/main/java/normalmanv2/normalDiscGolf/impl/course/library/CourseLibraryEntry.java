package normalmanv2.normalDiscGolf.impl.course.library;

public record CourseLibraryEntry(SavedCourse course, String name, boolean favorite) {
    public static final int MAX_NAME_LENGTH = 32;

    public CourseLibraryEntry {
        if (name != null) name = validateName(name);
    }

    public CourseLibraryEntry(SavedCourse course, String name) { this(course, name, false); }

    public CourseLibraryEntry withFavorite(boolean value) {
        return favorite == value ? this : new CourseLibraryEntry(course, name, value);
    }

    public String displayName() { return name == null ? course.label() : name; }

    public static String validateName(String input) {
        String name = input.strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > MAX_NAME_LENGTH
                || name.codePoints().anyMatch(point -> point == '§' || switch (Character.getType(point)) {
                    case Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.SURROGATE,
                            Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
                    default -> false;
                })) {
            throw new IllegalArgumentException("Use 1 to " + MAX_NAME_LENGTH + " visible characters for a course name.");
        }
        return name;
    }
}
