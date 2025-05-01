package normalmanv2.normalDiscGolf.impl.course.obstacle;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import normalmanv2.normalDiscGolf.api.course.obstacle.Obstacle;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.manager.course.ObstacleManager;
import org.bukkit.Location;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ObstacleImpl implements Obstacle, Cloneable {

    private Location location;
    private final String schematicName;
    private final ObstacleManager obstacleManager = NDGManager.getInstance().getObstacleManager();

    public ObstacleImpl(@Nullable Location location, String schematicName) {
        this.location = location;
        this.schematicName = schematicName;
    }

    @Override
    @Nonnull
    public Location getLocation() {
        return this.location;
    }

    @Override
    public String getSchematicName() {
        return this.schematicName;
    }

    @Override
    public void setLocation(Location location) {
        this.location = location;
    }

    @Override
    public void generate(Location location) {
        Optional<Path> schemFile = this.obstacleManager.getFile(this);

        if (schemFile.isEmpty()) throw new RuntimeException("Could not find schematic file");

        ClipboardFormat format = ClipboardFormats.findByFile(schemFile.get().toFile());
        if (format == null) {
            throw new RuntimeException("Unsupported schematic format: " + schemFile.get());
        }

        try (ClipboardReader reader = format.getReader(Files.newInputStream(schemFile.get()))) {
            Clipboard clipboard = reader.read();

            if (location.getWorld() == null)
                throw new RuntimeException("Error generating obstacle " + this + ". World is null!");

            World adaptedWorld = BukkitAdapter.adapt(location.getWorld());
            Location locationOffset = location.clone().add(0, 1, 0);
            BlockVector3 position = BukkitAdapter.asBlockVector(locationOffset);

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(adaptedWorld)) {
                ClipboardHolder holder = new ClipboardHolder(clipboard);

                Operation operation = holder.createPaste(editSession)
                        .ignoreAirBlocks(false)
                        .ignoreStructureVoidBlocks(false)
                        .to(position)
                        .build();

                Operations.complete(operation);

                System.out.println(location.getWorld().getName());
            } catch (WorldEditException exception) {
                throw new RuntimeException(exception);
            }
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public void generate() {
        this.generate(this.location);
    }

    @Override
    public ObstacleImpl clone() {
        try {
            return (ObstacleImpl) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
