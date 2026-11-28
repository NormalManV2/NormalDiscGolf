package normalmanv2.normalDiscGolf.impl.course.library;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGenerationSettings;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.round.RoundFormat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public record SavedCourse(long seed, int holes, int width, int depth, List<TileTypes> tileTypes,
                          CourseGrid.Layout layout, CourseGenerationSettings settings,
                          RoundFormat replayFormat, String preset, int par, int difficulty) {
    public SavedCourse {
        tileTypes = List.copyOf(tileTypes);
        if (!supportsDimensions(holes, width, depth) || tileTypes.isEmpty() || par < holes || difficulty < 0 || difficulty > 100)
            throw new IllegalArgumentException("Invalid saved course dimensions or stats");
        if (replayFormat != RoundFormat.FFA && replayFormat != RoundFormat.ACE_RACE && replayFormat != RoundFormat.CLOSEST_TO_PIN)
            throw new IllegalArgumentException("Invalid saved course format");
    }

    public static SavedCourse capture(CourseImpl course, RoundFormat format) {
        if (course.isCustom()) return null;
        if (course.isLibraryReplay()) return course.libraryCourse();
        var grid = course.grid();
        if (!supportsDimensions(course.holes(), grid.getWidth(), grid.getDepth())
                || course.holePars().size() != course.holes()) return null;
        RoundFormat replay = format == RoundFormat.ACE_RACE || format == RoundFormat.CLOSEST_TO_PIN ? format : RoundFormat.FFA;
        int par = course.holePars().values().stream().mapToInt(Integer::intValue).sum();
        return new SavedCourse(grid.seed(), course.holes(), grid.getWidth(), grid.getDepth(), grid.tileTypes(),
                grid.layout(), grid.terrainSettings(), replay, course.generationPreset(), par, grid.difficultyRating());
    }

    private static boolean supportsDimensions(int holes, int width, int depth) {
        return holes >= 1 && holes <= 18 && width >= 1 && width <= 128 && depth >= 1 && depth <= 128;
    }

    public UUID id() { return recipeId(replayFormat); }

    public UUID layoutId() { return recipeId(RoundFormat.FFA); }

    private UUID recipeId(RoundFormat format) {
        var recipe = List.of(CourseGrid.GENERATOR_VERSION, seed, holes, width, depth, tileTypes, layout,
                settings.terrain(), settings.elevation(), settings.fairwayWidth(), settings.obstacleDensity(),
                settings.hazardDensity(), settings.archetypes(), format);
        return UUID.nameUUIDFromBytes(recipe.toString().getBytes(StandardCharsets.UTF_8));
    }

    public String label() {
        return settings.terrain().label() + " • " + holes + (holes == 1 ? " hole" : " holes");
    }

    public CourseImpl instantiate(Division division, String worldName) {
        var grid = new CourseGrid(width, depth, tileTypes, holes, seed, layout, settings);
        return new CourseImpl(division, grid, worldName, preset, this);
    }
}
