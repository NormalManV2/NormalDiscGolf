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
    public enum Delivery {
        DISABLED("Vanilla icons · pack disabled", "This server has disabled custom disc models."),
        LOCAL_ONLY("Vanilla icons · local pack only", "Custom models are offered only to players connecting from the server machine."),
        UNAVAILABLE("Vanilla icons · pack unavailable", "The server could not offer its pack. Ask an admin to check the pack settings."),
        AVAILABLE("Vanilla icons · click to load", "Use /ndg pack retry to load the disc models."),
        OFFERED("Pack offered", "Accept the server resource pack prompt in Minecraft."),
        DOWNLOADING("Downloading the pack", "Minecraft is downloading the disc models."),
        APPLYING("Applying the pack", "Minecraft downloaded the pack and is applying the disc models."),
        LOADED("Custom disc models active", "Parkland disc models are active."),
        DECLINED("Pack declined · click to retry", "Enable server resource packs in Minecraft and use /ndg pack retry."),
        FAILED_DOWNLOAD("Download failed · click to retry", "Minecraft could not download the pack. Use /ndg pack retry."),
        FAILED_RELOAD("Pack could not load · click to retry", "Minecraft downloaded the pack but could not apply it. Use /ndg pack retry."),
        REMOVED("Pack removed · click to retry", "Minecraft removed the pack. Use /ndg pack retry.");

        private final String label;
        private final String guidance;

        Delivery(String label, String guidance) {
            this.label = label;
            this.guidance = guidance;
        }

        public String label() { return label; }
        public String guidance() { return guidance; }
        private boolean pending() { return this == OFFERED || this == DOWNLOADING || this == APPLYING; }
        private boolean canOffer() {
            return this == AVAILABLE || this == DECLINED || this == FAILED_DOWNLOAD || this == FAILED_RELOAD || this == REMOVED;
        }
    }

    private record PackArchive(byte[] bytes, byte[] hash, String etag) {}

    private static final UUID PACK_UUID = UUID.fromString("454c9909-7092-4e6b-bd65-f799099b1ab1");
    private final NormalDiscGolfPlugin plugin;
    private final DiscCatalog catalog;
    private final String packUrl;
    private final boolean enabled;
    private final boolean menuTextures;
    private final boolean localOnly;
    private final boolean required;
    private volatile PackArchive archive;
    private HttpServer server;
    private final Map<UUID, Delivery> deliveries = new HashMap<>();
    public PackedIntegration(NormalDiscGolfPlugin plugin, DiscCatalog catalog) throws IOException {
        this.plugin = plugin;
        this.catalog = catalog;
        var config = plugin.getConfig();
        enabled = config.getBoolean("resource-pack.enabled", true);
        menuTextures = config.getBoolean("resource-pack.menu-textures", true);
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
            PackArchive current = archive;
            byte[] bytes = current.bytes();
            String etag = current.etag();
            var headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/zip");
            headers.set("Cache-Control", "no-cache");
            headers.set("ETag", etag);
            var cached = exchange.getRequestHeaders().getOrDefault("If-None-Match", List.of());
            if (cached.stream().flatMap(value -> Arrays.stream(value.split(",")))
                    .map(String::strip).anyMatch(tag -> tag.equals(etag) || tag.equals("W/" + etag) || tag.equals("*"))) {
                exchange.sendResponseHeaders(304, -1);
                return;
            }
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
            MenuTextures.write(zip);
        }
        byte[] data = bytes.toByteArray();
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        var directory = plugin.getDataFolder().toPath();
        Files.createDirectories(directory);
        Files.write(directory.resolve("NormalDiscGolfPack.zip"), data);
        archive = new PackArchive(data, hash, "\"" + HexFormat.of().formatHex(hash) + "\"");
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
    public byte[] getBytes() { return archive.bytes().clone(); }
    public byte[] getHash() { return archive.hash().clone(); }
    public String getPackUrl() { return packUrl; }
    public Delivery delivery(Player player) {
        if (!enabled) return Delivery.DISABLED;
        if (!eligible(player)) return Delivery.LOCAL_ONLY;
        if (packUrl.isBlank()) return Delivery.UNAVAILABLE;
        return deliveries.getOrDefault(player.getUniqueId(), Delivery.AVAILABLE);
    }
    public boolean loaded(Player player) { return delivery(player) == Delivery.LOADED; }
    public boolean texturedMenus(Player player) { return menuTextures && loaded(player); }
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
        if (!delivery(player).canOffer()) return false;
        player.addResourcePack(PACK_UUID, packUrl, getHash(), "Parkland disc models and colors", required);
        deliveries.put(player.getUniqueId(), Delivery.OFFERED);
        return true;
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event) {
        if (!PACK_UUID.equals(event.getID())) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Delivery previous = deliveries.get(id);
        if (previous == null || !previous.pending() && previous != Delivery.LOADED) return;
        Delivery next = switch (event.getStatus()) {
            case ACCEPTED -> Delivery.DOWNLOADING;
            case DOWNLOADED -> Delivery.APPLYING;
            case SUCCESSFULLY_LOADED -> Delivery.LOADED;
            case DECLINED -> Delivery.DECLINED;
            case FAILED_DOWNLOAD, INVALID_URL -> Delivery.FAILED_DOWNLOAD;
            case FAILED_RELOAD -> Delivery.FAILED_RELOAD;
            case DISCARDED -> Delivery.REMOVED;
        };
        if (previous == next || previous == Delivery.LOADED && next.pending()
                || previous == Delivery.APPLYING && next == Delivery.DOWNLOADING) return;
        deliveries.put(id, next);
        refreshModels(player, previous == Delivery.LOADED || next == Delivery.LOADED);
        if (next == Delivery.LOADED) player.sendMessage("§aParkland disc models are ready.");
        else if (next.canOffer()) player.sendMessage((required ? "§eThe disc golf pack is required. " : "§eUsing vanilla disc icons. ")
                + next.guidance());
    }
    private void refreshModels(Player player, boolean modelsChanged) {
        if (modelsChanged && DiscFlightResolver.findActiveRound(player) != null) RoundPlayerListener.refreshDiscModels(player);
        NDGManager.getInstance().getGuiManager().refreshForPack(player);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        deliveries.remove(event.getPlayer().getUniqueId());
    }
    @Override public void close() {
        if (server != null) server.stop(0);
        deliveries.clear();
    }
}
