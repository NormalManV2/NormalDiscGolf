package normalmanv2.normalDiscGolf.impl.course;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CourseManager {

    private final Map<String, CourseImpl> courses;

    public CourseManager() {
        this.courses = new HashMap<>();
    }

    public void addCourse(String key, CourseImpl courseImpl) {
        this.courses.put(key, courseImpl);
    }

    public void removeCourse(String key) {
        this.courses.remove(key);
    }

    public CourseImpl getCourse(String key) {
        return this.courses.get(key);
    }

    public Map<String, CourseImpl> getCourses() {
        return Collections.unmodifiableMap(this.courses);
    }

}
