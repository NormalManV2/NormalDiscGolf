package normalmanv2.normalDiscGolf.impl.resourcepack;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

final class PackRequest {
    private static final Method ADD_PACK =
            find(Player.class, "addResourcePack", UUID.class, String.class, byte[].class, String.class, boolean.class);
    private static final Method PACK_ID = find(PlayerResourcePackStatusEvent.class, "getID");

    private PackRequest() {}

    static void send(Player player, UUID id, String url, byte[] hash, String prompt, boolean required) {
        if (ADD_PACK == null || PACK_ID == null) {
            player.setResourcePack(url, hash, prompt, required);
        } else {
            invoke(ADD_PACK, player, id, url, hash, prompt, required);
        }
    }

    static boolean matches(PlayerResourcePackStatusEvent event, UUID id) {
        return ADD_PACK == null || PACK_ID == null || id.equals(invoke(PACK_ID, event));
    }

    private static Method find(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException unavailable) {
            return null;
        }
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("Resource pack request failed", failure.getCause());
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Resource pack API is inaccessible", failure);
        }
    }
}
