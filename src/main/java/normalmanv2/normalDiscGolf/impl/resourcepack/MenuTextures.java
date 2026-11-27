package normalmanv2.normalDiscGolf.impl.resourcepack;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipOutputStream;

public final class MenuTextures {
    private static final int WIDTH = 176;
    private static final String MODEL_PATH = "gui/";
    private static final Map<Material, Icon> ICONS = icons();

    private enum Icon {
        CLUBHOUSE(0xccae76, "....#..|...###.|..#####|.######|#######|.##+##.|.##+##.", Material.SPRUCE_SIGN),
        CLOCK(0xf4c46d, "..###..|.#...#.|#..+..#|#..++.#|#.....#|.#...#.|..###..", Material.CLOCK),
        BASKET(0xa3e0b4, ".#####.|..#+#..|..#+#..|.+###+.|..###..|...#...|..###..", Material.TARGET, Material.IRON_BARS),
        BAG(0x8ecded, "..###..|.#+.+#.|.#####.|##+++##|##+.+##|#######|.#####.", Material.BUNDLE),
        COMPASS(0x91e0cd, "..###..|.##.+#.|##.++.#|#..+..#|#.++..#|.#.###.|..###..", Material.COMPASS),
        NAV(0xebce9c, "...#...|..###..|.+###+.|#######|.+###+.|..###..|...#...", Material.ARROW),
        ROUNDS(0x79deb4, "######.|#+++.##|#++...#|#######|#......|#......|###....", Material.EMERALD),
        BOOK(0xe3ce9d, "..#.#..|###.###|#++#++#|#+.#.+#|#++#++#|#######|..###..", Material.WRITTEN_BOOK, Material.WRITABLE_BOOK, Material.BOOK),
        CARD(0xefdcb2, ".#####.|.#+++##|.#++++#|.#+##+#|.#+##+#|.#++++#|.######", Material.PAPER),
        CREDIT(0xf4bc5b, "..###..|.#####.|##+####|##+++##|####+##|.#####.|..###..", Material.GOLD_NUGGET),
        MATCH(0xffcc78, "#.....#|##...##|.##.##.|..###..|..###..|.+#.#.+|#+...+#", Material.GOLD_INGOT),
        TEAM(0x92d5ff, "..#.#..|.##.##.|.+#.#+.|.......|.##.##.|#######|#######", Material.DIAMOND),
        DISC(0x99e6a8, ".......|..###..|.##+##.|#######|.#####.|..###..|.......", Material.SLIME_BALL, Material.LIME_DYE),
        TURN(0xf2a96b, ".###...|.#.##..|.+.###.|....###|.+.###.|.#.##..|.###...", Material.ORANGE_DYE),
        CRYSTAL(0xe4b6ff, "...#...|..#+#..|.##+##.|###++##|.##+##.|..###..|...#...", Material.AMETHYST_SHARD),
        STAR(0xf4b6e7, "...#...|...#...|..###..|#######|..###..|...#...|...#...", Material.FIREWORK_STAR, Material.EXPERIENCE_BOTTLE),
        FLAME(0xf8ac72, "...#...|..##...|..###..|.####..|.##+##.|##+++##|.#####.", Material.BLAZE_POWDER, Material.FIREWORK_ROCKET),
        PIN(0xeaa867, "#######|#+++++#|#+++++#|#######|#......|#......|###....", Material.GOLDEN_HELMET, Material.ENDER_EYE),
        IMAGE(0x9bcbec, "#######|#+++++#|#+#+++#|#+++#.#|#.###.#|#+++++#|#######", Material.PAINTING, Material.SPYGLASS),
        CLOSE(0xf09990, ".......|.#...#.|..#.#..|...#...|..#.#..|.#...#.|.......", Material.BARRIER),
        TREE(0x9fdf9c, "...#...|..###..|...#...|.#####.|...#...|#######|...+...", Material.SPRUCE_SAPLING, Material.GRASS_BLOCK),
        MOUNTAIN(0xbed5e7, "...#...|..#+#..|..#+##.|.##.#+#|.##.###|#######|#######", Material.SNOW_BLOCK),
        DESERT(0xe6aa7a, "...#...|...#+..|..###..|..###..|.#####.|#######|#######", Material.RED_SAND),
        ISLAND(0x8ad9d7, "...#...|..###..|.#....#|...#...|.#####.|#######|.+.+.+.", Material.HEART_OF_THE_SEA),
        NETHER(0xf59a93, ".#...#.|.##.##.|.#####.|#.+.+.#|#######|.#####.|..###..", Material.CRIMSON_FUNGUS),
        SKY(0xc3d1fa, "..###..|.#####.|#######|#######|.#####.|...+...|.......", Material.FEATHER),
        LOCK(0x9aa8ad, "..###..|..#.#..|..#.#..|.#####.|.##+##.|.##+##.|.#####.", Material.BLACK_STAINED_GLASS_PANE),
        UNDER_PAR(0x85dead, ".......|......#|.....##|#...##.|##.##..|.###...|..#....", Material.GREEN_STAINED_GLASS_PANE),
        EVEN_PAR(0xf4c46d, ".......|.#####.|.......|.......|.#####.|.......|.......", Material.YELLOW_STAINED_GLASS_PANE),
        OVER_PAR(0xf4a29e, ".......|...#...|...#...|.#####.|...#...|...#...|.......", Material.RED_STAINED_GLASS_PANE),
        UPCOMING(0x9eafa6, ".......|.......|.......|.#####.|.......|.......|.......", Material.GRAY_STAINED_GLASS_PANE),
        FAIRWAY(0x7ac9ab, "...#...|..###..|..#+#..|...+...|..+++..|.+++++.|#######", Material.MOSS_BLOCK),
        PUTT(0xa4d8f5, "....+#.|..+...#|.+.....|..###..|.##+##.|#######|.#####.", Material.LIGHT_BLUE_DYE),
        PENALTY(0xf09990, "...#...|..#+#..|..#+#..|.##+##.|.#####.|###+###|#######", Material.REDSTONE);

        private final int color;
        private final String pixels;
        private final NamespacedKey key = new NamespacedKey("ndg", MODEL_PATH + name().toLowerCase(Locale.ROOT));
        private final Material[] materials;

        Icon(int color, String pixels, Material... materials) {
            this.color = color;
            this.pixels = pixels;
            this.materials = materials;
        }
    }

    private MenuTextures() {}

    private static Map<Material, Icon> icons() {
        Map<Material, Icon> icons = new EnumMap<>(Material.class);
        for (Icon icon : Icon.values()) for (Material material : icon.materials) icons.put(material, icon);
        return icons;
    }

    public static ItemStack apply(ItemStack item, boolean textured) {
        if (item == null) return null;
        Icon icon = ICONS.get(item.getType());
        if (icon == null) return item;
        var meta = item.getItemMeta();
        if (meta == null) return item;
        NamespacedKey current = meta.getItemModel();
        boolean menuIcon = current != null && "ndg".equals(current.getNamespace()) && current.getKey().startsWith(MODEL_PATH);
        if (current != null && !menuIcon) return item;
        NamespacedKey model = textured ? icon.key : null;
        if (java.util.Objects.equals(current, model)) return item;
        meta.setItemModel(model);
        ItemStack updated = item.clone();
        updated.setItemMeta(meta);
        return updated;
    }

    static void write(ZipOutputStream zip) throws IOException {
        var providers = new ArrayList<String>();
        // Chest titles start at x=8. Move to x=0 for the bitmap, then return to x=12 for readable text.
        providers.add("{\"type\":\"space\",\"advances\":{\"%s\":-8,\"%s\":%d}}"
                .formatted(MenuSkin.LEFT, MenuSkin.LABEL, 12 - (WIDTH + 1)));
        for (MenuSkin skin : MenuSkin.values()) {
            String path = "gui/panel/" + skin.id() + ".png";
            int height = 18 + skin.rows() * 18;
            providers.add("""
                    {"type":"bitmap","file":"ndg:%s","ascent":13,"height":%d,"chars":["%s"]}
                    """.formatted(path, height, skin.glyph()).strip());
            png(zip, path, panel(skin, height));
        }
        for (RoundHudSkin badge : RoundHudSkin.values()) {
            String path = "gui/hud/" + badge.name().toLowerCase(Locale.ROOT) + ".png";
            providers.add("""
                    {"type":"bitmap","file":"ndg:%s","ascent":8,"height":10,"chars":["%s"]}
                    """.formatted(path, badge.glyph()).strip());
            png(zip, path, hudBadge(switch (badge) {
                case BANNER -> Icon.CLUBHOUSE;
                case HOLE -> Icon.ROUNDS;
                case DISTANCE -> Icon.BASKET;
                case STROKES -> Icon.CARD;
                case SCORE -> Icon.STAR;
                case TURN -> Icon.NAV;
                case TIME -> Icon.CLOCK;
                case STANDINGS -> Icon.MATCH;
            }));
        }
        PackedIntegration.entry(zip, "assets/minecraft/font/default.json",
                "{\"providers\":[" + String.join(",", providers) + "]}");
        for (Icon icon : Icon.values()) {
            String model = icon.key.getKey();
            png(zip, "item/" + model + ".png", iconTexture(icon));
            PackedIntegration.entry(zip, "assets/ndg/models/item/" + model + ".json",
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"ndg:item/" + model + "\"}}");
            PackedIntegration.entry(zip, "assets/ndg/items/" + model + ".json",
                    "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"ndg:item/" + model + "\"}}");
        }
    }

    private static BufferedImage panel(MenuSkin skin, int height) {
        var image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D art = image.createGraphics();
        rect(art, 0x172725, 0, 0, WIDTH, height);
        rect(art, 0x365047, 1, 1, WIDTH - 2, height - 2);
        rect(art, 0x253631, 4, 17, WIDTH - 8, height - 20);
        rect(art, 0x172522, 4, 3, WIDTH - 8, 12);
        rect(art, skin.accent(), 4, 15, WIDTH - 8, 1);
        rect(art, 0x111d1a, 4, 16, WIDTH - 8, 1);
        for (int y = 3; y <= 13; y += 2) {
            int left = 101 + (y * 11) % 15;
            rect(art, 0x253d36, left, y, WIDTH - left - 5, 1);
        }
        for (int offset : new int[]{120, 131, 143}) {
            art.setColor(new Color(0x436256));
            art.fillPolygon(new int[]{offset, offset + 6, offset + 12}, new int[]{13, 5, 13}, 3);
            rect(art, 0x365047, offset + 5, 8, 2, 6);
        }
        rect(art, skin.accent(), 158, 7, 11, 2);
        rect(art, skin.accent(), 156, 9, 11, 1);
        rect(art, 0x121d1a, 7, height - 2, WIDTH - 14, 1);
        // Keep the item wells transparent because the title is rendered over the menu.
        art.setComposite(AlphaComposite.Clear);
        for (int row = 0; row < skin.rows(); row++) for (int col = 0; col < 9; col++)
            art.fillRect(8 + col * 18, 18 + row * 18, 16, 16);
        art.dispose();
        return image;
    }

    private static BufferedImage iconTexture(Icon icon) {
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D art = image.createGraphics();
        rect(art, 0x0b1a18, 4, 2, 24, 28);
        rect(art, 0x0b1a18, 2, 4, 28, 24);
        rect(art, 0x375047, 5, 3, 22, 26);
        rect(art, 0x375047, 3, 5, 26, 22);
        rect(art, 0x1c302b, 5, 6, 22, 20);
        rect(art, icon.color, 7, 27, 18, 1);
        paintSymbol(art, icon, 9, 8, 2);
        art.dispose();
        return image;
    }

    private static BufferedImage hudBadge(Icon icon) {
        var image = new BufferedImage(11, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D art = image.createGraphics();
        rect(art, 0x344e45, 0, 0, 11, 10);
        rect(art, 0x172725, 1, 1, 9, 8);
        paintSymbol(art, icon, 2, 1, 1);
        art.dispose();
        return image;
    }

    private static void paintSymbol(Graphics2D art, Icon icon, int left, int top, int size) {
        String[] rows = icon.pixels.split("\\|");
        for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++) {
            char pixel = rows[y].charAt(x);
            if (pixel != '.') rect(art, pixel == '+' ? 0xfff2ce : icon.color, left + size * x, top + size * y, size, size);
        }
    }

    private static void rect(Graphics2D art, int color, int x, int y, int width, int height) {
        art.setColor(new Color(color));
        art.fillRect(x, y, width, height);
    }

    private static void png(ZipOutputStream zip, String path, BufferedImage image) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        PackedIntegration.entry(zip, "assets/ndg/textures/" + path, output.toByteArray());
    }
}
