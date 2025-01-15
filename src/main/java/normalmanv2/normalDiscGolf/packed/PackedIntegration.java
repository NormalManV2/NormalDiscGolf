package normalmanv2.normalDiscGolf.packed;

import kotlin.Unit;
import net.radstevee.packed.core.asset.impl.SourceDirectoryAssetResolutionStrategy;
import net.radstevee.packed.core.key.Key;
import net.radstevee.packed.core.pack.PackFormat;
import net.radstevee.packed.core.pack.ResourcePack;
import net.radstevee.packed.core.pack.ResourcePackBuilder;
import normalmanv2.normalDiscGolf.NormalDiscGolf;

import java.io.File;

public class PackedIntegration {

    private final ResourcePack pack;

    protected PackedIntegration(NormalDiscGolf plugin) {
        this.pack = ResourcePackBuilder.Companion.resourcePack((builder) -> {
            builder.meta((meta) -> {
                meta.setDescription("NDG Pack");
                meta.setOutputDir(new File(plugin.getDataFolder(), "NormalDiscGolfPack"));
                meta.setFormat(PackFormat.LATEST);

                return Unit.INSTANCE;
            });

            builder.setAssetResolutionStrategy(new SourceDirectoryAssetResolutionStrategy(new File("Test")));
            builder.create();
            return Unit.INSTANCE;
        });
    }

    public void savePack() {

        this.pack.addItemModel(new Key("ndg", "disc1"), (model) -> {


        });
        this.pack.save(true);

    }

}
