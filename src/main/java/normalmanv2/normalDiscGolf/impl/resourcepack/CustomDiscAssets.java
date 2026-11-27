package normalmanv2.normalDiscGolf.impl.resourcepack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipOutputStream;

/** A Java item model with local textures rewritten into a private namespace path for its disc. */
public final class CustomDiscAssets {
    private static final int MAX_MODEL_BYTES = 256 * 1024;
    private static final int MAX_TEXTURE_BYTES = 1024 * 1024;
    private final String model;
    private final Map<String, byte[]> textures;

    private CustomDiscAssets(String model, Map<String, byte[]> textures) {
        this.model = model;
        this.textures = Collections.unmodifiableMap(new TreeMap<>(textures));
    }

    public static CustomDiscAssets load(Path discFolder, String id) throws IOException {
        Path modelFile = discFolder.resolve("model.json");
        if (!Files.exists(modelFile, LinkOption.NOFOLLOW_LINKS)) return null;
        JsonObject model;
        try {
            model = JsonParser.parseString(new String(read(modelFile, discFolder, MAX_MODEL_BYTES), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (model.has("parent")) {
                String parent = model.get("parent").getAsString();
                if (!parent.contains(":")) parent = "minecraft:" + parent;
                if (!parent.startsWith("minecraft:")) throw new IllegalArgumentException("only vanilla parent models are supported");
                resourcePath(parent.substring("minecraft:".length()));
                model.addProperty("parent", parent);
            } else if (!model.has("elements") || model.getAsJsonArray("elements").isEmpty()) {
                throw new IllegalArgumentException("model needs elements or a vanilla parent");
            }

            Map<String, byte[]> textures = new TreeMap<>();
            if (model.has("textures")) {
                JsonObject references = model.getAsJsonObject("textures");
                if (references.size() > 24) throw new IllegalArgumentException("model has more than 24 texture references");
                for (Map.Entry<String, JsonElement> entry : references.entrySet()) {
                    String value = entry.getValue().getAsString();
                    if (value.startsWith("#")) continue;
                    String resource = resourcePath(value.substring(value.indexOf(':') + 1));
                    Path paths = discFolder.resolve("textures");
                    Path file = paths.resolve(resource + ".png");
                    if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                        file = paths.resolve(Path.of(resource).getFileName() + ".png");
                    }
                    if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS) && value.startsWith("minecraft:")) continue;

                    String key = "custom/" + id + "/" + resource;
                    if (!textures.containsKey(key)) {
                        byte[] bytes = read(file, discFolder, MAX_TEXTURE_BYTES);
                        validateTexture(bytes, file);
                        textures.put(key, bytes);
                    }
                    entry.setValue(new com.google.gson.JsonPrimitive("ndg:" + key));
                }
            }
            return new CustomDiscAssets(model.toString(), textures);
        } catch (RuntimeException invalid) {
            throw new IOException("invalid model.json: " + invalid.getMessage(), invalid);
        }
    }

    private static String resourcePath(String path) {
        if (!path.matches("[a-z0-9_./-]+") || path.startsWith("/") || path.endsWith("/"))
            throw new IllegalArgumentException("invalid asset reference: " + path);
        for (String component : path.split("/")) {
            if (component.isEmpty() || component.equals(".") || component.equals(".."))
                throw new IllegalArgumentException("invalid asset reference: " + path);
        }
        return path;
    }

    private static byte[] read(Path file, Path root, int limit) throws IOException {
        Path target = file.toRealPath();
        if (!target.startsWith(root.toRealPath()) || !Files.isRegularFile(target))
            throw new IOException("asset must be inside the disc folder: " + file.getFileName());
        try (var input = Files.newInputStream(target)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("asset is too large: " + file.getFileName());
            return bytes;
        }
    }

    private static void validateTexture(byte[] bytes, Path file) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("expected a PNG texture: " + file.getFileName());
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (!reader.getFormatName().equalsIgnoreCase("png") || reader.getWidth(0) > 512 || reader.getHeight(0) > 512)
                    throw new IOException("expected a PNG up to 512x512: " + file.getFileName());
                reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    void write(ZipOutputStream zip, String id) throws IOException {
        PackedIntegration.entry(zip, "assets/ndg/models/item/" + id + ".json", model);
        for (Map.Entry<String, byte[]> texture : textures.entrySet())
            PackedIntegration.entry(zip, "assets/ndg/textures/" + texture.getKey() + ".png", texture.getValue());
    }
}
