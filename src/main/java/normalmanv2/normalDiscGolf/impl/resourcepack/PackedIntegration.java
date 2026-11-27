package normalmanv2.normalDiscGolf.impl.resourcepack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.DiscCatalog;
import normalmanv2.normalDiscGolf.impl.disc.DiscFlightResolver;
import normalmanv2.normalDiscGolf.impl.listener.RoundPlayerListener;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Builds a self-contained 1.21.8 pack. Public delivery is configured by the server owner. */
public final class PackedIntegration implements Listener, AutoCloseable {
    private static final UUID PACK_UUID = UUID.fromString("454c9909-7092-4e6b-bd65-f799099b1ab1");
    private final NormalDiscGolfPlugin plugin;
    private final DiscCatalog catalog;
    private final String packUrl;
    private final boolean enabled;
    private final boolean localOnly;
    private final boolean required;
    private byte[] packBytes, packHash;
    private HttpServer server;
    private final Set<UUID> loaded = new HashSet<>();
    private final Set<UUID> pending = new HashSet<>();
    public PackedIntegration(NormalDiscGolfPlugin plugin, DiscCatalog catalog) throws IOException {
        this.plugin = plugin;
        this.catalog = catalog;
        var config = plugin.getConfig();
        enabled = config.getBoolean("resource-pack.enabled", true);
        boolean serve = config.getBoolean("resource-pack.serve", true);
        int port = config.getInt("resource-pack.port", 8088);
        String configuredUrl = config.getString("resource-pack.url", "").trim();
        boolean automaticLocal = enabled && configuredUrl.isEmpty() && serve
                && config.getBoolean("resource-pack.local-auto", true);
        String requestedUrl = automaticLocal ? "http://127.0.0.1:" + port + "/ndg/pack.zip" : configuredUrl;
        required = automaticLocal ? config.getBoolean("resource-pack.local-required", true)
                : config.getBoolean("resource-pack.required", false);
        savePack();

        boolean loopbackOnly = automaticLocal;
        String deliveryUrl = "";
        if (enabled && !requestedUrl.isBlank()) {
            try {
                URI uri = URI.create(requestedUrl);
                if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())
                        || uri.getHost() == null)
                    throw new IllegalArgumentException("resource-pack.url must be an absolute HTTP(S) URL");
                loopbackOnly = isLoopbackHost(uri.getHost());
                if (serve) startServer(uri.getHost(), loopbackOnly, port);
                deliveryUrl = requestedUrl;
            } catch (IOException | IllegalArgumentException failure) {
                plugin.getLogger().warning("Could not offer the disc models: " + failure.getMessage());
            }
        } else if (enabled && required) {
            plugin.getLogger().warning("The disc golf pack is required but no delivery URL is configured.");
        }
        localOnly = loopbackOnly;
        packUrl = deliveryUrl;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void startServer(String host, boolean loopbackOnly, int port) throws IOException {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("resource-pack.port must be between 1 and 65535");
        String loopback = host.equalsIgnoreCase("localhost") ? "127.0.0.1" : host;
        var bind = loopbackOnly ? new InetSocketAddress(loopback, port) : new InetSocketAddress(port);
        server = HttpServer.create(bind, 0);
        server.createContext("/ndg/pack.zip", this::servePack);
        server.start();
    }

    private void servePack(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestURI().getPath().equals("/ndg/pack.zip")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            boolean head = exchange.getRequestMethod().equals("HEAD");
            if (!head && !exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] bytes = packBytes;
            var headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/zip");
            headers.set("Cache-Control", "no-cache");
            headers.set("Content-Length", Integer.toString(bytes.length));
            exchange.sendResponseHeaders(200, head ? -1 : bytes.length);
            if (!head) exchange.getResponseBody().write(bytes);
        }
    }
    public void savePack() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, "pack.mcmeta", "{\"pack\":{\"pack_format\":64,\"description\":\"Normal Disc Golf • Parkland\"}}");
            DiscPackModels.write(zip, catalog);
        }
        packBytes=bytes.toByteArray();
        try { packHash=MessageDigest.getInstance("SHA-1").digest(packBytes); } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        Files.createDirectories(plugin.getDataFolder().toPath());
        Files.write(plugin.getDataFolder().toPath().resolve("NormalDiscGolfPack.zip"),packBytes);
    }
    static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        entry(zip, name, content.getBytes(StandardCharsets.UTF_8));
    }
    static void entry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }
    public byte[] getBytes() { return packBytes; }
    public byte[] getHash() { return packHash; }
    public String getPackUrl() { return packUrl; }
    public boolean loaded(Player player) { return loaded.contains(player.getUniqueId()); }
    public boolean pending(Player player) { return pending.contains(player.getUniqueId()); }
    public boolean ready(Player player) { return !enabled || !required || !eligible(player) || loaded(player); }

    private boolean eligible(Player player) {
        if (!localOnly) return true;
        var socket = player.getAddress();
        return socket != null && socket.getAddress() != null && socket.getAddress().isLoopbackAddress();
    }

    private static boolean isLoopbackHost(String host) {
        return host.equalsIgnoreCase("localhost") || host.matches("127(?:\\.\\d{1,3}){3}")
                || host.equals("[::1]") || host.equals("::1");
    }

    public boolean send(Player player) {
        if (!enabled || !eligible(player) || packUrl.isBlank() || loaded(player) || pending(player)) return false;
        player.addResourcePack(PACK_UUID, packUrl, packHash, "Parkland disc models and colors", required);
        pending.add(player.getUniqueId());
        return true;
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event) {
        if (!PACK_UUID.equals(event.getID())) return;
        Player player = event.getPlayer();
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            pending.remove(player.getUniqueId());
            if (loaded.add(player.getUniqueId())) {
                player.sendMessage("§aParkland disc models are ready.");
                refreshModels(player);
            }
        } else if (event.getStatus() != PlayerResourcePackStatusEvent.Status.ACCEPTED
                && event.getStatus() != PlayerResourcePackStatusEvent.Status.DOWNLOADED) {
            pending.remove(player.getUniqueId());
            loaded.remove(player.getUniqueId());
            refreshModels(player);
            player.sendMessage(required
                    ? "§eThe disc golf pack is required. Enable server resource packs and reconnect."
                    : "§eUsing vanilla disc icons. Use /ndg pack retry to try the models again.");
        }
    }
    private void refreshModels(Player player) {
        if (DiscFlightResolver.findActiveRound(player) != null) RoundPlayerListener.refreshDiscModels(player);
        NDGManager.getInstance().getGuiManager().refreshForPack(player);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
        pending.remove(event.getPlayer().getUniqueId());
    }
    @Override public void close() { if(server!=null) server.stop(0); loaded.clear(); pending.clear(); }
}
