package normalmanv2.normalDiscGolf.test;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

public class EmptyChunkGenerator extends ChunkGenerator {

    
    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) {
        return super.canSpawn(world, x, z);
    }

    @Override
    public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        super.generateBedrock(worldInfo, random, chunkX, chunkZ, chunkData);
    }

    public EmptyChunkGenerator() {
        super();
    }

    @Override
    public void generateCaves(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        super.generateCaves(worldInfo, random, chunkX, chunkZ, chunkData);
    }

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        super.generateNoise(worldInfo, random, chunkX, chunkZ, chunkData);
    }

    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        super.generateSurface(worldInfo, random, chunkX, chunkZ, chunkData);
    }

    @Override
    public int getBaseHeight(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull HeightMap heightMap) {
        return super.getBaseHeight(worldInfo, random, x, z, heightMap);
    }

    @Nullable
    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return super.getDefaultBiomeProvider(worldInfo);
    }

    @NotNull
    @Override
    public List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return super.getDefaultPopulators(world);
    }

    @Nullable
    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return super.getFixedSpawnLocation(world, random);
    }

    @Override
    public boolean shouldGenerateCaves() {
        return super.shouldGenerateCaves();
    }

    @Override
    public boolean shouldGenerateCaves(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ) {
        return super.shouldGenerateCaves(worldInfo, random, chunkX, chunkZ);
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return super.shouldGenerateDecorations();
    }

    @Override
    public boolean shouldGenerateDecorations(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ) {
        return super.shouldGenerateDecorations(worldInfo, random, chunkX, chunkZ);
    }

    @Override
    public boolean shouldGenerateMobs() {
        return super.shouldGenerateMobs();
    }

    @Override
    public boolean shouldGenerateMobs(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ) {
        return super.shouldGenerateMobs(worldInfo, random, chunkX, chunkZ);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return super.shouldGenerateNoise();
    }

    @Override
    public boolean shouldGenerateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ) {
        return super.shouldGenerateNoise(worldInfo, random, chunkX, chunkZ);
    }

    @Override
    public boolean shouldGenerateStructures() {
        return super.shouldGenerateStructures();
    }

    @Override
    public boolean shouldGenerateStructures(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ) {
        return super.shouldGenerateStructures(worldInfo, random, chunkX, chunkZ);
    }

    @Override
    public boolean shouldGenerateSurface() {
        return super.shouldGenerateSurface();
    }

    @Override
    public boolean shouldGenerateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ) {
        return super.shouldGenerateSurface(worldInfo, random, chunkX, chunkZ);
    }
}
