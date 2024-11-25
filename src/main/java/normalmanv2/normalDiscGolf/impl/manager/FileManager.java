package normalmanv2.normalDiscGolf.impl.manager;

import normalmanv2.normalDiscGolf.NormalDiscGolf;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class FileManager {

    private final Path obstacleFolder;

    public FileManager(NormalDiscGolf normalDiscGolf) {
        this.obstacleFolder = Paths.get(normalDiscGolf.getDataFolder().getPath(), "obstacles");
        try {
            if (Files.notExists(this.obstacleFolder)) {
                Files.createDirectories(this.obstacleFolder);
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Get an obstacle schematic file by its name.
     *
     * @param fileName The name of the schematic file (without extension).
     * @return An Optional containing the Path if it exists, or an empty Optional if not found.
     */
    public Optional<Path> getObstacle(String fileName) {
        Path schematicPath = this.obstacleFolder.resolve(fileName + ".schem");
        if (Files.exists(schematicPath) && Files.isRegularFile(schematicPath)) {
            return Optional.of(schematicPath);
        }
        return Optional.empty();
    }

    /**
     * Get the directory where obstacle schematics are stored.
     *
     * @return The obstacle folder as a Path.
     */
    public Path getObstacleFolder() {
        return this.obstacleFolder;
    }

    /**
     * List all schematic files in the obstacle folder.
     *
     * @return A Stream of Path objects representing all schematic files.
     * @throws IOException If an I/O error occurs while reading the directory.
     */
    public DirectoryStream<Path> listObstacles() throws IOException {
        return Files.newDirectoryStream(this.obstacleFolder, "*.schem");
    }

    /**
     * Delete a specific schematic file by its name.
     *
     * @param fileName The name of the schematic file to delete.
     * @return true if the file was successfully deleted, false otherwise.
     */
    public boolean deleteObstacle(String fileName) {
        Path schematicPath = this.obstacleFolder.resolve(fileName + ".schem");
        try {
            return Files.deleteIfExists(schematicPath);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

}
