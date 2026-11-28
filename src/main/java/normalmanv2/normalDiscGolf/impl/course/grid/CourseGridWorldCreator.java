package normalmanv2.normalDiscGolf.impl.course.grid;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import org.bukkit.WorldCreator;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Objects;

public class CourseGridWorldCreator extends WorldCreator {

    private final CourseImpl course;

    public CourseGridWorldCreator(@NotNull String name, CourseImpl course, Division division) {
        super(name);
        this.course = course;
        generateStructures(false);
        course.grid().generate(division);
        seed(course.grid().seed());
        if (course.grid().isThemed()) {
            if (course.grid().terrain() == CourseTerrain.NETHER) environment(World.Environment.NETHER);
            var biomeKey = NamespacedKey.minecraft(course.grid().terrain().biome());
            Biome biome = Objects.requireNonNull(Registry.BIOME.get(biomeKey), () -> "Missing biome " + biomeKey);
            biomeProvider(new BiomeProvider() {
                @Override public Biome getBiome(WorldInfo world, int x, int y, int z) { return biome; }
                @Override public List<Biome> getBiomes(WorldInfo world) { return List.of(biome); }
            });
        }
    }

    @Override
    public @Nullable ChunkGenerator generator() {
        return new CourseGridChunkGenerator(this.course.grid());
    }


}
