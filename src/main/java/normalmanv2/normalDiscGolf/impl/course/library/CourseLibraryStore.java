package normalmanv2.normalDiscGolf.impl.course.library;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseLibraryStore {
    enum PinResult { PINNED, ALREADY_PINNED, UNAVAILABLE }

    record FeaturedCourse(String code, CourseLibraryEntry entry) {}

    default boolean save(UUID owner, SavedCourse course) throws IOException {
        return save(owner, new CourseLibraryEntry(course, null));
    }

    boolean save(UUID owner, CourseLibraryEntry entry) throws IOException;
    List<CourseLibraryEntry> list(UUID owner) throws IOException;
    Optional<CourseLibraryEntry> find(UUID owner, UUID course) throws IOException;
    boolean rename(UUID owner, UUID course, String name) throws IOException;
    Optional<CourseLibraryEntry> setFavorite(UUID owner, UUID course, boolean favorite) throws IOException;
    Optional<String> shareCode(UUID owner, UUID course) throws IOException;
    Optional<String> share(UUID owner, UUID course) throws IOException;
    boolean revokeShare(UUID owner, UUID course) throws IOException;
    Optional<CourseLibraryEntry> resolveShare(String code) throws IOException;
    List<FeaturedCourse> featuredCourses() throws IOException;
    Optional<CourseLibraryEntry> resolveFeatured(String code) throws IOException;
    PinResult pin(String code) throws IOException;
    boolean unpin(String code) throws IOException;
}
