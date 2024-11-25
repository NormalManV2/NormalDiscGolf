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
import normalmanv2.normalDiscGolf.impl.manager.ObstacleManager;
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
    private final ObstacleManager obstacleManager;

    public ObstacleImpl(@Nullable Location location, String schematicName, ObstacleManager obstacleManager) {
        this.location = location;
        this.schematicName = schematicName;
        this.obstacleManager = obstacleManager;
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
    public boolean generate(Location location) {
        Optional<Path> schemFile = obstacleManager.getFile(this);

        if (schemFile.isEmpty()) return false;

        ClipboardFormat format = ClipboardFormats.findByFile(schemFile.get().toFile());
        if (format == null) {
            System.err.println("Unsupported schematic format: " + schemFile.get());
            return false;
        }

        try (ClipboardReader reader = format.getReader(Files.newInputStream(schemFile.get()))) {
            Clipboard clipboard = reader.read();

            if (location.getWorld() == null)
                throw new RuntimeException("Error generating obstacle " + this + " World is null!");

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
            } catch (WorldEditException e) {
                throw new RuntimeException(e);
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean generate() {
        return this.generate(this.location);
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
