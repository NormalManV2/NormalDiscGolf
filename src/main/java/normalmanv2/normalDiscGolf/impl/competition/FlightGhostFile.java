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

/** The compact recording format shared by Daily and saved-layout ghosts. */
public final class FlightGhostFile {
    private static final int VERSION = 1;

    private FlightGhostFile() {}

    public static FlightGhost load(Path file, long seed, int holeCount, int strokeLimit, int strokesTotal) throws IOException {
        if (Files.notExists(file)) return null;
        try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != VERSION || in.readLong() != seed || in.readUnsignedByte() != holeCount)
                throw new IOException("Ghost does not match the course");
            var holes = new ArrayList<FlightGhost.Hole>(holeCount);
            for (int hole = 0; hole < holeCount; hole++) {
                int strokes = in.readInt();
                int throwsInHole = in.readInt();
                if (strokes < 1 || strokes > Math.min(strokeLimit, strokesTotal) || throwsInHole < 1 || throwsInHole > strokes)
                    throw new IOException("Invalid ghost stroke count");
                var paths = new ArrayList<List<FlightGhost.Point>>();
                for (int shot = 0; shot < throwsInHole; shot++) {
                    int count = in.readUnsignedByte();
                    if (count < 2 || count > FlightGhost.MAX_POINTS_PER_THROW) throw new IOException("Invalid ghost flight length");
                    var points = new ArrayList<FlightGhost.Point>(count);
                    for (int point = 0; point < count; point++)
                        points.add(new FlightGhost.Point(in.readShort(), in.readShort(), in.readShort()));
                    paths.add(points);
                }
                holes.add(new FlightGhost.Hole(strokes, paths));
            }
            var ghost = new FlightGhost(holes);
            if (ghost.totalStrokes() != strokesTotal || in.read() != -1) throw new IOException("Invalid ghost score or data");
            return ghost;
        }
    }

    public static boolean saveIfMissing(Path destination, long seed, int holeCount, int strokeLimit,
                                       int strokesTotal, FlightGhost ghost) throws IOException {
        if (ghost.holes().size() != holeCount || ghost.totalStrokes() != strokesTotal
                || ghost.holes().stream().anyMatch(hole -> hole.strokes() > strokeLimit))
            throw new IOException("Recording does not match the course score");
        try {
            if (load(destination, seed, holeCount, strokeLimit, strokesTotal) != null) return false;
        } catch (IOException invalidRecording) {
            // A matching completed attempt can repair an unreadable ghost.
        }
        Files.createDirectories(destination.getParent());
        Path temp = Files.createTempFile(destination.getParent(), "ghost-", ".tmp");
        try {
            try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(VERSION);
                out.writeLong(seed);
                out.writeByte(holeCount);
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

}
