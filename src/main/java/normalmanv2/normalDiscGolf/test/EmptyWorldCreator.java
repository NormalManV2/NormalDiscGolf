package normalmanv2.normalDiscGolf.test;

import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

public class EmptyWorldCreator extends WorldCreator {

    public EmptyWorldCreator(String name) {
        super(name);
    }

    @Override
    public ChunkGenerator generator() {
        return new EmptyChunkGenerator();
    }
}
