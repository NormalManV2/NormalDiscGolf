package normalmanv2.normalDiscGolf.impl.course.world;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.chunk.CourseGridChunkGenerator;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CourseGridWorldCreator extends WorldCreator {

    private final CourseImpl course;
    private final Division division;

    public CourseGridWorldCreator(@NotNull String name, CourseImpl course, Division division) {
        super(name);
        this.course = course;
        this.division = division;
    }

    @Override
    public @Nullable ChunkGenerator generator() {
        return new CourseGridChunkGenerator(this.course.grid(), this.division);
    }
}
