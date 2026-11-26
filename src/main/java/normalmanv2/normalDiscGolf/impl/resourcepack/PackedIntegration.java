package normalmanv2.normalDiscGolf.impl.resourcepack;

import com.sun.net.httpserver.HttpServer;
import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
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
    private final String packUrl;
    private final boolean enabled;
    private final boolean localOnly;
    private final boolean required;
    private byte[] packBytes, packHash;
    private HttpServer server;
    private final Set<UUID> loaded = new HashSet<>();
    public PackedIntegration(NormalDiscGolfPlugin plugin) throws IOException {
        this.plugin = plugin;
        var config = plugin.getConfig();
        enabled = config.getBoolean("resource-pack.enabled", true);
        boolean serve = config.getBoolean("resource-pack.serve", true);
        int port = config.getInt("resource-pack.port", 8088);
        String configuredUrl = config.getString("resource-pack.url", "").trim();
        boolean automaticLocal = enabled && configuredUrl.isEmpty() && serve
                && config.getBoolean("resource-pack.local-auto", true);
        packUrl = automaticLocal ? "http://127.0.0.1:" + port + "/ndg/pack.zip" : configuredUrl;
        required = automaticLocal ? config.getBoolean("resource-pack.local-required", true)
                : config.getBoolean("resource-pack.required", false);
        savePack();

        String url = getPackUrl();
        if (enabled && !url.isBlank()) {
            URI uri = URI.create(url);
            if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) throw new IOException("resource-pack.url must be an absolute HTTP(S) URL");
            localOnly = isLoopbackHost(uri.getHost());
            if (serve) {
                String loopback = uri.getHost().contains(":") ? "::1" : "127.0.0.1";
                var bind = localOnly ? new InetSocketAddress(loopback, port) : new InetSocketAddress(port);
                server = HttpServer.create(bind, 0);
                server.createContext("/ndg/pack.zip", exchange -> {
                    try {
                        if (!exchange.getRequestMethod().equals("GET")) { exchange.sendResponseHeaders(405,-1); return; }
                        exchange.getResponseHeaders().set("Content-Type","application/zip");
                        exchange.sendResponseHeaders(200, packBytes.length);
                        exchange.getResponseBody().write(packBytes);
                    } finally { exchange.close(); }
                });
                server.start();
            }
        } else {
            localOnly = false;
            if (enabled && required) plugin.getLogger().warning("The disc golf pack is required but no delivery URL is configured.");
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    public void savePack() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, "pack.mcmeta", "{\"pack\":{\"pack_format\":64,\"description\":\"Normal Disc Golf • Parkland\"}}");
            String[] names={"putter","midrange","driver"}, colors={"lime_concrete","light_blue_concrete","orange_concrete"};
            for (int i=0;i<names.length;i++) {
                entry(zip,"assets/ndg/items/"+names[i]+".json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"ndg:item/"+names[i]+"\"}}");
                String model = """
                        {"textures":{"disc":"minecraft:block/%s","particle":"minecraft:block/%s"},
                        "display":{"gui":{"rotation":[30,30,0],"scale":[1.1,1.1,1.1]},
                        "firstperson_righthand":{"rotation":[0,0,-15],"translation":[0,1,0],"scale":[0.7,0.7,0.7]},
                        "ground":{"scale":[0.6,0.6,0.6]}},
                        "elements":[%s,%s,%s]}
                        """.formatted(colors[i], colors[i], element(3,7,1,13,8,15), element(1,7,3,15,8,13), element(2,7,2,14,8,14));
                entry(zip,"assets/ndg/models/item/"+names[i]+".json",model);
            }
        }
        packBytes=bytes.toByteArray();
        try { packHash=MessageDigest.getInstance("SHA-1").digest(packBytes); } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        Files.createDirectories(plugin.getDataFolder().toPath());
        Files.write(plugin.getDataFolder().toPath().resolve("NormalDiscGolfPack.zip"),packBytes);
    }
    private static String element(int x,int y,int z,int xx,int yy,int zz) {
        String face="{\"texture\":\"#disc\"}";
        return "{\"from\":["+x+","+y+","+z+"],\"to\":["+xx+","+yy+","+zz+"],\"faces\":{\"up\":"+face+",\"down\":"+face+",\"north\":"+face+",\"south\":"+face+",\"east\":"+face+",\"west\":"+face+"}}";
    }
    private static void entry(ZipOutputStream zip,String name,String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
    public byte[] getBytes() { return packBytes; }
    public byte[] getHash() { return packHash; }
    public String getPackUrl() { return packUrl; }
    public boolean loaded(Player player) { return loaded.contains(player.getUniqueId()); }
    public boolean ready(Player player) { return !enabled || !required || !eligible(player) || loaded(player); }

    private boolean eligible(Player player) {
        if (!localOnly) return true;
        var socket = player.getAddress();
        return socket != null && socket.getAddress() != null && socket.getAddress().isLoopbackAddress();
    }

    private static boolean isLoopbackHost(String host) {
        return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
                || host.equals("[::1]") || host.equals("::1");
    }

    public void send(Player player) {
        if (enabled && eligible(player) && !packUrl.isBlank())
            player.addResourcePack(PACK_UUID,packUrl,packHash,"Disc models for NormalDiscGolf",required);
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event) {
        if (!PACK_UUID.equals(event.getID())) return;
        if(event.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) loaded.add(event.getPlayer().getUniqueId());
        else if (event.getStatus() != PlayerResourcePackStatusEvent.Status.ACCEPTED
                && event.getStatus() != PlayerResourcePackStatusEvent.Status.DOWNLOADED) {
            loaded.remove(event.getPlayer().getUniqueId());
            event.getPlayer().sendMessage(required
                    ? "§eThe disc golf pack is required. Enable server resource packs and reconnect."
                    : "§eDisc models unavailable. You can play using the labeled vanilla items.");
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event) { loaded.remove(event.getPlayer().getUniqueId()); }
    @Override public void close() { if(server!=null) server.stop(0); loaded.clear(); }
}
