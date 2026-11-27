package normalmanv2.normalDiscGolf.impl.resourcepack;

import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.disc.DiscCatalog;
import normalmanv2.normalDiscGolf.impl.disc.DiscAppearance;
import normalmanv2.normalDiscGolf.impl.disc.ArcadeDisc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

final class DiscPackModels {
    private DiscPackModels() {}

    static void write(ZipOutputStream zip, DiscCatalog catalog) throws IOException {
        var visibleModels = new ArrayList<>(catalog.models());
        visibleModels.addAll(ArcadeDisc.models());
        for (DiscModel disc : visibleModels) {
            String id = disc.id();
            modelEntry(zip, id, id);
            CustomDiscAssets custom = catalog.assets(disc);
            if (custom != null) {
                custom.write(zip, id);
                continue;
            }
            DiscAppearance colors = disc.appearance();
            PackedIntegration.entry(zip, "assets/ndg/textures/item/" + id + "_plate.png", texture(colors, true));
            PackedIntegration.entry(zip, "assets/ndg/textures/item/" + id + "_rim.png", texture(colors, false));
            PackedIntegration.entry(zip, "assets/ndg/models/item/" + id + ".json", model(disc));
        }
        modelEntry(zip, "putter", DiscModel.STARTER_PUTTER.id());
        modelEntry(zip, "midrange", DiscModel.STARTER_MIDRANGE.id());
        modelEntry(zip, "driver", DiscModel.STARTER_DRIVER.id());
    }

    private static void modelEntry(ZipOutputStream zip, String item, String model) throws IOException {
        PackedIntegration.entry(zip, "assets/ndg/items/" + item + ".json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"ndg:item/" + model + "\"}}");
    }

    private static String model(DiscModel disc) {
        List<String> elements = new ArrayList<>();
        for (Layer layer : layers(disc.appearance().shape())) addLayer(elements, layer);
        return """
                {"textures":{"plate":"ndg:item/%1$s_plate","rim":"ndg:item/%1$s_rim","particle":"#plate"},
                "display":{
                  "gui":{"rotation":[52,30,0],"scale":[1.12,1.12,1.12],"translation":[0,-0.5,0]},
                  "firstperson_righthand":{"rotation":[5,0,-16],"translation":[0,1,0],"scale":[0.68,0.68,0.68]},
                  "firstperson_lefthand":{"rotation":[5,0,16],"translation":[0,1,0],"scale":[0.68,0.68,0.68]},
                  "ground":{"scale":[0.62,0.62,0.62]}},
                "elements":[%2$s]}
                """.formatted(disc.id(), String.join(",", elements));
    }

    private record Layer(double outer, double inner, double fromY, double toY, String texture) {}

    private static List<Layer> layers(DiscAppearance.Shape shape) {
        return switch (shape) {
            case CLASSIC -> List.of(new Layer(7, 0, 6, 8.5, "#rim"), new Layer(6.2, 0, 8.5, 9.4, "#plate"));
            case ROUNDED -> List.of(new Layer(6.2, 0, 6.2, 7, "#rim"), new Layer(7.35, 0, 7, 8.1, "#rim"),
                    new Layer(6.55, 0, 8.1, 8.9, "#plate"));
            case DOME -> List.of(new Layer(7.3, 0, 6.9, 7.8, "#rim"), new Layer(6.8, 0, 7.8, 8.5, "#plate"),
                    new Layer(5.4, 0, 8.5, 9.12, "#plate"), new Layer(3.65, 0, 9.12, 9.5, "#plate"));
            case PUDDLE -> List.of(new Layer(7.2, 0, 6.3, 7.7, "#rim"), new Layer(6.85, 0, 7.7, 8.15, "#plate"),
                    new Layer(7.2, 5.35, 8.15, 9.2, "#plate"));
            case BEVELED -> List.of(new Layer(5.35, 0, 6.25, 6.9, "#rim"), new Layer(7.45, 0, 6.9, 7.7, "#rim"),
                    new Layer(6.25, 0, 7.7, 8.55, "#plate"));
            case LOW_PROFILE -> List.of(new Layer(7.6, 0, 7.25, 7.95, "#rim"),
                    new Layer(6.8, 0, 7.95, 8.4, "#plate"));
        };
    }

    private static void addLayer(List<String> elements, Layer layer) {
        for (int z = 0; z < 16; z++) {
            double dz = z - 7.5;
            int half = crossSection(layer.outer, dz);
            if (half == 0) continue;
            int inner = crossSection(layer.inner, dz);
            if (inner == 0) {
                elements.add(element(8 - half, layer.fromY, z, 8 + half, layer.toY, z + 1, layer.texture));
            } else if (inner < half) {
                elements.add(element(8 - half, layer.fromY, z, 8 - inner, layer.toY, z + 1, layer.texture));
                elements.add(element(8 + inner, layer.fromY, z, 8 + half, layer.toY, z + 1, layer.texture));
            }
        }
    }

    private static int crossSection(double radius, double dz) {
        return Math.abs(dz) >= radius ? 0 : (int) Math.round(Math.sqrt(radius * radius - dz * dz));
    }

    private static String element(int x, double y, int z, int xx, double yy, int zz, String texture) {
        String cap = "{\"uv\":[" + x + "," + z + "," + xx + "," + zz + "],\"texture\":\"" + texture + "\"}";
        String edge = "{\"texture\":\"" + texture + "\"}";
        return "{\"from\":[" + x + "," + y + "," + z + "],\"to\":[" + xx + "," + yy + "," + zz + "],"
                + "\"faces\":{\"up\":" + cap + ",\"down\":" + cap + ",\"north\":" + edge + ",\"south\":"
                + edge + ",\"east\":" + edge + ",\"west\":" + edge + "}}";
    }

    private static byte[] texture(DiscAppearance colors, boolean plate) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double radius = Math.hypot(x - 7.5, y - 7.5);
            int rgb;
            if (!plate) rgb = y % 4 == 0 ? colors.accent() : colors.rim();
            else if (radius >= 4.9 && radius < 5.9 || Math.abs(x - y) <= 1 && radius < 3.8) rgb = colors.accent();
            else rgb = colors.plate();
            image.setRGB(x, y, 0xff000000 | rgb);
        }
        var png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        return png.toByteArray();
    }

}
