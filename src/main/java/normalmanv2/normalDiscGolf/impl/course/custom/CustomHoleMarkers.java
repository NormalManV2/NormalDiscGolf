package normalmanv2.normalDiscGolf.impl.course.custom;

import normalmanv2.normalDiscGolf.impl.util.BukkitCompatibility;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Round-local visuals leave the builder's blocks and entities alone. */
public final class CustomHoleMarkers {
    private final List<Entity> displays = new ArrayList<>();

    public void show(CustomCourse course, org.bukkit.World world, int hole) {
        clear();
        Location tee = course.tee(world, hole);
        Location basket = course.basket(world, hole).subtract(0, .9, 0);
        label(tee.clone().add(0, 2.65, 0), "§a§lTEE " + (hole + 1) + "  §fPar " + course.holePars().get(hole));
        label(basket.clone().add(0, 2.25, 0), "§6§lBASKET " + (hole + 1));
        block(basket, Material.IRON_BLOCK, -.075f, 0, -.075f, .15f, 1.8f, .15f);
        block(basket, Material.IRON_BLOCK, -.55f, .68f, -.55f, 1.1f, .09f, 1.1f);
        block(basket, Material.YELLOW_STAINED_GLASS, -.55f, .77f, -.55f, 1.1f, .24f, 1.1f);
        block(basket, Material.CHAIN, -.28f, .99f, -.28f, .56f, .73f, .56f);
        block(basket, Material.YELLOW_CONCRETE, -.49f, 1.68f, -.49f, .98f, .14f, .98f);
    }

    public void clear() {
        displays.forEach(Entity::remove);
        displays.clear();
    }

    private void label(Location at, String text) {
        displays.add(BukkitCompatibility.spawn(at, TextDisplay.class, display -> {
            display.setText(text);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setPersistent(false);
        }));
    }

    private void block(Location at, Material material, float x, float y, float z, float width, float height, float depth) {
        displays.add(BukkitCompatibility.spawn(at, BlockDisplay.class, display -> {
            display.setBlock(material.createBlockData());
            display.setTransformation(new Transformation(new Vector3f(x, y, z), new Quaternionf(),
                    new Vector3f(width, height, depth), new Quaternionf()));
            display.setPersistent(false);
        }));
    }
}
