package normalmanv2.normalDiscGolf.impl.resourcepack;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

final class ResourcePackNetworking {
    private ResourcePackNetworking() {
    }

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final InetSocketAddress localhost = InetSocketAddress.createUnresolved("localhost", Bukkit.getPort());

    private static InetSocketAddress cachedServerAddress;
    private static Boolean externalAddressAccessible;

    private static final URI CHECKIP_URL = URI.create("checkip.amazonaws.com");

    @NotNull static InetSocketAddress getExternalAddress() {
        if (cachedServerAddress != null) {
            return cachedServerAddress;
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    HttpRequest.newBuilder(CHECKIP_URL).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        String host = response.body().strip();
        int port = Bukkit.getPort();
        InetSocketAddress address = InetSocketAddress.createUnresolved(host, port);

        cachedServerAddress = address;

        return address;
    }

    static @NotNull String getServerUri(InetSocketAddress serverAddress, String path) {
        return "http://" + serverAddress.getHostString() + ":" + serverAddress.getPort() + "/ndg/" + path;
    }

    static @NotNull String getServerUri(String path) {
        return getServerUri(getExternalAddress(), path);
    }

    static boolean isExternalAddressAccessible() {
        if (externalAddressAccessible != null) {
            return externalAddressAccessible;
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(getServerUri("check"))).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (IOException | InterruptedException e) {
            externalAddressAccessible = false;
            return false;
        }

        externalAddressAccessible = response.statusCode() == 200 && response.body().equals("Yes");
        return externalAddressAccessible;
    }

    static @NotNull String getPackUrl() {
        String url;
        if (isExternalAddressAccessible()) {
            url = getServerUri(localhost, "pack");
        } else {
            url = getServerUri("pack");
        }

        return url;
    }
}
