package normalmanv2.normalDiscGolf.impl.manager.file;

import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import org.bukkit.Bukkit;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class FileManager {

    private static final List<String> REQUIRED_OBSTACLE_SCHEMATICS = List.of(
            "bush",
            "pin",
            "rock",
            "tee",
            "tree"
    );

    private final NormalDiscGolfPlugin plugin;
    private final Path obstacleFolder;
    private final List<String> missingRequiredObstacles;

    public FileManager(NormalDiscGolfPlugin normalDiscGolfPlugin) {
        this.plugin = normalDiscGolfPlugin;
        this.obstacleFolder = Paths.get(normalDiscGolfPlugin.getDataFolder().getPath(), "obstacles");
        this.missingRequiredObstacles = new ArrayList<>();

        this.ensureObstacleFolderExists();
        this.installBundledObstacleDefaults();
        this.validateRequiredObstacles();
    }

    private void ensureObstacleFolderExists() {
        try {
            if (Files.notExists(this.obstacleFolder)) {
                Files.createDirectories(this.obstacleFolder);
            }
        } catch (IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not create obstacle folder " + this.obstacleFolder, exception);
        }
    }

    private void installBundledObstacleDefaults() {
        for (String obstacleName : REQUIRED_OBSTACLE_SCHEMATICS) {
            Path target = this.obstacleFolder.resolve(obstacleName + ".schem");
            if (this.isUsableSchematic(target)) {
                continue;
            }

            String resourcePath = "obstacles/" + obstacleName + ".schem";
            try (InputStream inputStream = this.plugin.getResource(resourcePath)) {
                if (inputStream == null) {
                    this.plugin.getLogger().warning("Missing bundled obstacle schematic resource: " + resourcePath);
                    continue;
                }

                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                this.plugin.getLogger().info("Installed default obstacle schematic " + target.getFileName());
            } catch (IOException exception) {
                this.plugin.getLogger().log(Level.WARNING, "Could not install default obstacle schematic " + obstacleName, exception);
            }
        }
    }

    public void validateRequiredObstacles() {
        this.validateRequiredObstacles(true);
    }

    private void validateRequiredObstacles(boolean logResult) {
        this.missingRequiredObstacles.clear();

        for (String obstacleName : REQUIRED_OBSTACLE_SCHEMATICS) {
            Path schematicPath = this.obstacleFolder.resolve(obstacleName + ".schem");
            if (!this.isUsableSchematic(schematicPath)) {
                this.missingRequiredObstacles.add(obstacleName);
            }
        }

        if (this.missingRequiredObstacles.isEmpty()) {
            if (logResult) {
                this.plugin.getLogger().info("All required obstacle schematics are available.");
            }
            return;
        }

        if (logResult) {
            this.plugin.getLogger().warning("Missing required obstacle schematics: " + String.join(", ", this.missingRequiredObstacles));
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
        if (this.isUsableSchematic(schematicPath)) {
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

    public boolean areRequiredObstaclesAvailable() {
        this.validateRequiredObstacles(false);
        return this.missingRequiredObstacles.isEmpty();
    }

    public List<String> getMissingRequiredObstacles() {
        this.validateRequiredObstacles(false);
        return Collections.unmodifiableList(this.missingRequiredObstacles);
    }

    public List<String> getRequiredObstacleSchematics() {
        return Collections.unmodifiableList(REQUIRED_OBSTACLE_SCHEMATICS);
    }

    public String getMissingRequiredObstaclesMessage() {
        List<String> missingObstacles = this.getMissingRequiredObstacles();
        if (missingObstacles.isEmpty()) {
            return "All required obstacle schematics are available.";
        }

        return "Missing required obstacle schematics: " + String.join(", ", missingObstacles)
                + ". Add them to " + this.obstacleFolder + " or rebuild the plugin with bundled defaults.";
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

    private boolean isUsableSchematic(Path schematicPath) {
        if (!Files.exists(schematicPath) || !Files.isRegularFile(schematicPath)) {
            return false;
        }

        try {
            if (Files.size(schematicPath) <= 0) {
                return false;
            }
            if (!Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) return true;

            ClipboardFormat format = ClipboardFormats.findByFile(schematicPath.toFile());
            if (format == null) {
                return false;
            }

            try (ClipboardReader reader = format.getReader(Files.newInputStream(schematicPath))) {
                reader.read();
                return true;
            }
        } catch (IOException exception) {
            return false;
        }
    }
}
