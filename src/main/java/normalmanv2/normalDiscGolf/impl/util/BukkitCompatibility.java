package normalmanv2.normalDiscGolf.impl.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class BukkitCompatibility {
    public static final Particle DUST = particle("DUST", "REDSTONE");
    public static final Particle FIREWORK = particle("FIREWORK", "FIREWORKS_SPARK");
    public static final Particle HAPPY_VILLAGER = particle("HAPPY_VILLAGER", "VILLAGER_HAPPY");
    public static final Material SHORT_GRASS = material("SHORT_GRASS", "GRASS");
    public static final Material POLISHED_TUFF = material("POLISHED_TUFF", "TUFF");

    private static final Method MODEL_GETTER = find(ItemMeta.class, "getItemModel");
    private static final Method MODEL_SETTER = find(ItemMeta.class, "setItemModel", NamespacedKey.class);
    private static final Method WAX_GETTER = find(Sign.class, "isWaxed");
    private static final Method WAX_SETTER = find(Sign.class, "setWaxed", boolean.class);
    private static final Method TELEPORT_DURATION = find(Display.class, "setTeleportDuration", int.class);
    private static final Method LEVEL_DIRECTORY = find(Server.class, "getLevelDirectory");
    private static final Method SPAWN = spawnMethod();

    private BukkitCompatibility() {}

    public static boolean hasItemModels() { return MODEL_GETTER != null && MODEL_SETTER != null; }
    public static NamespacedKey itemModel(ItemMeta meta) { return (NamespacedKey) invoke(MODEL_GETTER, meta); }
    public static void itemModel(ItemMeta meta, NamespacedKey model) { invoke(MODEL_SETTER, meta, model); }
    public static boolean needsWax(Sign sign) { return Boolean.FALSE.equals(invoke(WAX_GETTER, sign)); }
    public static void setWaxed(Sign sign, boolean waxed) { invoke(WAX_SETTER, sign, waxed); }
    public static void teleportDuration(Display display, int ticks) { invoke(TELEPORT_DURATION, display, ticks); }

    public static Path generatedWorldDirectory(String name) {
        Path level = (Path) invoke(LEVEL_DIRECTORY, Bukkit.getServer());
        return level == null ? Bukkit.getWorldContainer().toPath().resolve(name)
                : level.resolve("dimensions").resolve(NamespacedKey.MINECRAFT).resolve(name);
    }

    public static <T extends Entity> T spawn(Location location, Class<T> type, Consumer<? super T> initialize) {
        var consumerType = SPAWN.getParameterTypes()[2];
        Object callback = initialize;
        if (consumerType != Consumer.class) {
            callback = Proxy.newProxyInstance(consumerType.getClassLoader(), new Class<?>[]{consumerType},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) return invoke(method, initialize, args);
                        initialize.accept(type.cast(args[0]));
                        return null;
                    });
        }
        return type.cast(invoke(SPAWN, location.getWorld(), location, type, callback));
    }

    private static Method spawnMethod() {
        var modern = find(World.class, "spawn", Location.class, Class.class, Consumer.class);
        if (modern != null) return modern;
        // Bukkit 1.19.4 uses its own Consumer interface for the pre-spawn initializer.
        try {
            var legacy = Class.forName("org.bukkit.util.Consumer", false, World.class.getClassLoader());
            return World.class.getMethod("spawn", Location.class, Class.class, legacy);
        } catch (ReflectiveOperationException unavailable) {
            throw new IllegalStateException("Bukkit pre-spawn initializer is unavailable", unavailable);
        }
    }

    private static Particle particle(String current, String legacy) {
        try {
            return Particle.valueOf(current);
        } catch (IllegalArgumentException unavailable) {
            return Particle.valueOf(legacy);
        }
    }

    private static Material material(String current, String legacy) {
        Material preferred = Material.getMaterial(current);
        return preferred == null ? Material.valueOf(legacy) : preferred;
    }

    private static Method find(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException unavailable) {
            return null;
        }
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        if (method == null) return null;
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("Bukkit compatibility call failed", failure.getCause());
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Bukkit compatibility API is inaccessible", failure);
        }
    }
}
