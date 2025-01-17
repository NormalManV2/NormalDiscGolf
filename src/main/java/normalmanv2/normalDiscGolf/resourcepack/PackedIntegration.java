package normalmanv2.normalDiscGolf.resourcepack;

import kotlin.Unit;
import net.radstevee.packed.core.asset.impl.ResourceAssetResolutionStrategy;
import net.radstevee.packed.core.pack.PackFormat;
import net.radstevee.packed.core.pack.ResourcePack;
import net.radstevee.packed.core.pack.ResourcePackBuilder;
import normalmanv2.normalDiscGolf.NormalDiscGolf;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class PackedIntegration {

    private static final UUID PACK_UUID = UUID.fromString("454c9909-7092-4e6b-bd65-f799099b1ab1");

    private final ResourcePack pack;
    private final NormalDiscGolf plugin;
    private byte[] packBytes;
    private byte[] packHash;

    public PackedIntegration(NormalDiscGolf plugin) throws IOException {
        this.plugin = plugin;
        this.pack = ResourcePackBuilder.Companion.resourcePack((builder) -> {
            builder.meta((meta) -> {
                meta.setDescription("NDG Pack");
                meta.setOutputDir(new File(plugin.getDataFolder(), "NormalDiscGolfPack"));
                meta.setFormat(PackFormat.LATEST);

                return Unit.INSTANCE;
            });

            builder.setAssetResolutionStrategy(new ResourceAssetResolutionStrategy(this.getClass()));
            builder.create();
            return Unit.INSTANCE;
        });

        this.savePack();
    }

    public void savePack() throws IOException {
        this.pack.save(true);
        File zip = new File(plugin.getDataFolder(), "NormalDiscGolfPack.zip");
        this.pack.createZip(zip);

        this.packBytes = Files.readAllBytes(zip.toPath());

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        md5.update(this.packBytes);
        this.packHash = md5.digest();
    }

    public byte[] getBytes() {
        return this.packBytes;
    }

    public byte[] getHash() {
        return this.packHash;
    }

    public String getPackUrl() {
        return ResourcePackNetworking.getPackUrl();
    }

    public void send(@NotNull Player player) {
        player.addResourcePack(PACK_UUID, this.getPackUrl(), this.getHash(), "You need this resource pack in order to play NormalDiscGolf.", true);
    }

}
