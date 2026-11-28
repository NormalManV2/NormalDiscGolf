package normalmanv2.normalDiscGolf.impl.resourcepack;

import normalmanv2.normalDiscGolf.impl.disc.ArcadeDisc;
import normalmanv2.normalDiscGolf.impl.disc.DiscCatalog;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import org.bukkit.Material;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

/** Pre-item-model clients select the same NDG disc assets through CustomModelData. */
final class LegacyDiscModels {
    private record Model(DiscModel disc, Material carrier) {}

    private final Map<String, Integer> numbers = new HashMap<>();
    private final Map<Material, List<String>> overrides = new EnumMap<>(Material.class);

    LegacyDiscModels(DiscCatalog catalog) {
        var entries = new ArrayList<Model>();
        for (var disc : catalog.models()) entries.add(new Model(disc, DiscItems.discMaterial(disc.type())));
        for (var disc : ArcadeDisc.all()) entries.add(new Model(disc.model(), disc.icon()));
        entries.sort(Comparator.comparing(model -> model.disc().id()));
        for (var entry : entries) {
            String id = entry.disc().id();
            int number = 300_000 + numbers.size();
            numbers.put(id, number);
            overrides.computeIfAbsent(entry.carrier(), key -> new ArrayList<>())
                    .add("{\"predicate\":{\"custom_model_data\":" + number + "},\"model\":\"ndg:item/" + id + "\"}");
        }
    }

    Integer modelData(DiscModel disc) { return numbers.get(disc.id()); }

    void write(ZipOutputStream zip) throws IOException {
        for (var entry : overrides.entrySet()) {
            var carrier = entry.getKey().getKey().getKey();
            PackedIntegration.entry(zip, "assets/minecraft/models/item/" + carrier + ".json",
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/" + carrier
                            + "\"},\"overrides\":[" + String.join(",", entry.getValue()) + "]}");
        }
    }
}
