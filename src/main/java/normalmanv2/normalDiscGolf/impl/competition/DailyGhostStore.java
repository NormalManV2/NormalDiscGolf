package normalmanv2.normalDiscGolf.impl.competition;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Score-specific files let a failed or interrupted ghost write leave the leaderboard intact. */
final class DailyGhostStore {
    private static final int VERSION = 1;
    private final Path directory;

    DailyGhostStore(Path directory) { this.directory = directory; }

    DailyGhost load(DailyCourse.Definition course, StrokeStandings.Entry best) throws IOException {
        Path file = file(course, best);
        if (Files.notExists(file)) return null;
        try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != VERSION || in.readLong() != course.seed() || in.readUnsignedByte() != course.holes())
                throw new IOException("Ghost does not match the Daily Course");
            var holes = new ArrayList<DailyGhost.Hole>(course.holes());
            for (int hole = 0; hole < course.holes(); hole++) {
                int strokes = in.readInt();
                int throwsInHole = in.readInt();
                if (strokes < 1 || strokes > course.strokeLimit() || throwsInHole < 1 || throwsInHole > strokes)
                    throw new IOException("Invalid ghost stroke count");
                var paths = new ArrayList<List<DailyGhost.Point>>();
                for (int shot = 0; shot < throwsInHole; shot++) {
                    int count = in.readUnsignedByte();
                    if (count < 2 || count > DailyGhost.MAX_POINTS_PER_THROW) throw new IOException("Invalid ghost flight length");
                    var points = new ArrayList<DailyGhost.Point>(count);
                    for (int point = 0; point < count; point++)
                        points.add(new DailyGhost.Point(in.readShort(), in.readShort(), in.readShort()));
                    paths.add(points);
                }
                holes.add(new DailyGhost.Hole(strokes, paths));
            }
            var ghost = new DailyGhost(holes);
            if (ghost.totalStrokes() != best.strokes() || in.read() != -1) throw new IOException("Invalid ghost score or data");
            return ghost;
        }
    }

    boolean saveIfMissing(DailyCourse.Definition course, StrokeStandings.Entry best, DailyGhost ghost) throws IOException {
        if (ghost.holes().size() != course.holes() || ghost.totalStrokes() != best.strokes()
                || ghost.holes().stream().anyMatch(hole -> hole.strokes() > course.strokeLimit()))
            throw new IOException("Recording does not match the Daily Course score");
        try {
            if (load(course, best) != null) return false;
        } catch (IOException invalidRecording) {
            // A matching completed attempt can repair an unreadable ghost.
        }
        Path destination = file(course, best);
        Files.createDirectories(destination.getParent());
        Path temp = Files.createTempFile(destination.getParent(), "ghost-", ".tmp");
        try {
            try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(VERSION);
                out.writeLong(course.seed());
                out.writeByte(course.holes());
                for (var hole : ghost.holes()) {
                    out.writeInt(hole.strokes());
                    out.writeInt(hole.throwsInOrder().size());
                    for (var path : hole.throwsInOrder()) {
                        out.writeByte(path.size());
                        for (var point : path) {
                            out.writeShort(point.x16());
                            out.writeShort(point.y16());
                            out.writeShort(point.z16());
                        }
                    }
                }
            }
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return true;
    }

    private Path file(DailyCourse.Definition course, StrokeStandings.Entry best) {
        return directory.resolve(course.date().toString()).resolve(best.player() + "-" + best.strokes() + ".ndg");
    }
}
