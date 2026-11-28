package normalmanv2.normalDiscGolf.impl.admin;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.library.FileCourseLibraryStore;
import normalmanv2.normalDiscGolf.impl.util.Constants;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class CourseUpgradeCheck {
    record Result(String signature, String problem) {
        boolean passed() { return signature != null; }
    }

    private CourseUpgradeCheck() {}

    static Result verify(String filename, Reader recipe) {
        try {
            var saved = FileCourseLibraryStore.readRecipe(filename, recipe).course();
            CourseGrid grid = saved.instantiate(Division.JUNIOR, "upgrade-check").grid();
            grid.generate(Division.JUNIOR);
            int par = 0;
            var routes = grid.getHoleRoutePoints();
            var fingerprint = sha256();
            // Ordered anchors, pars, routes, and terrain samples describe the playable layout.
            for (int hole = 0; hole < saved.holes(); hole++) {
                var tee = grid.getTeePoints().get(hole);
                var basket = grid.getHolePoints().get(hole);
                var route = routes.get(hole);
                var holePar = grid.getHolePars().get(hole);
                if (tee == null || basket == null || route == null || route.size() < 2 || holePar == null
                        || holePar < 1 || !grid.inHoleCell(hole, tee.x(), tee.z())
                        || !grid.inHoleCell(hole, basket.x(), basket.z()) || tee.equals(basket)
                        || grid.surfaceType(tee.x(), tee.z()).isHazard() || grid.surfaceType(basket.x(), basket.z()).isHazard())
                    return new Result(null, "Hole " + (hole + 1) + " did not regenerate with valid tee, basket, route, and par.");
                par += holePar;
                add(fingerprint, tee + "|" + basket + "|" + holePar + "|" + route + "|" + grid.openingTarget(hole));
            }
            if (par != saved.par()) return new Result(null, "Generated par " + par + " differs from saved par " + saved.par() + ".");
            int tileSize = Constants.DEFAULT_TILE_SIZE;
            for (int x = tileSize / 2; x < grid.getWidth() * tileSize; x += tileSize) {
                for (int z = tileSize / 2; z < grid.getDepth() * tileSize; z += tileSize) {
                    add(fingerprint, grid.isThemed() ? grid.terrainColumn(x, z).toString()
                            : grid.surfaceHeight(x, z) + "|" + grid.surfaceType(x, z));
                }
            }
            return new Result(HexFormat.of().formatHex(fingerprint.digest()), null);
        } catch (IOException unreadable) {
            return new Result(null, unreadable.getMessage());
        } catch (RuntimeException invalid) {
            return new Result(null, "The course generator could not rebuild this layout (" + invalid.getClass().getSimpleName() + ").");
        }
    }

    private static void add(MessageDigest digest, String line) {
        digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
