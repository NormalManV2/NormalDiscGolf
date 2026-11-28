package normalmanv2.normalDiscGolf.impl.admin;

import org.bukkit.configuration.ConfigurationSection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Mirrors ZIPs and their checksums to an authenticated HTTPS collection. */
public final class OffsiteBackups {
    private static final int CHECKSUM_LIMIT = 96;
    private static final int AUTHORIZATION_LIMIT = 4096;
    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final DataBackups local;
    private final URI base;
    private final String authorizationEnv;
    private final Path authorizationFile;

    public OffsiteBackups(DataBackups local, ConfigurationSection config, Path dataDirectory) {
        this.local = local;
        this.base = endpoint(config.getString("backups.offsite.url", ""));
        this.authorizationEnv = config.getString("backups.offsite.authorization-env", "NDG_BACKUP_AUTHORIZATION").strip();
        this.authorizationFile = config.getBoolean("backups.offsite.authorization-file", false)
                ? dataDirectory.resolve("offsite-authorization.txt") : null;
    }

    static URI endpoint(String value) {
        String text = value.strip();
        if (text.isEmpty()) return null;
        try {
            var parsed = URI.create(text);
            if ("https".equalsIgnoreCase(parsed.getScheme()) && parsed.getHost() != null
                    && (parsed.getPort() == -1 || parsed.getPort() > 0 && parsed.getPort() <= 65535)
                    && parsed.getUserInfo() == null && parsed.getQuery() == null && parsed.getFragment() == null)
                return URI.create(parsed.toASCIIString() + (parsed.getRawPath().endsWith("/") ? "" : "/"));
        } catch (IllegalArgumentException invalid) { }
        throw new IllegalArgumentException("Use a private HTTPS directory URL without credentials, a query, or a fragment.");
    }

    public boolean enabled() { return base != null; }

    public String upload(String filename) throws IOException {
        String auth = authorization();
        Path file = local.verifiedArchive(filename);
        return mirror(filename, auth, HttpRequest.BodyPublishers.ofFile(file), checksum(file));
    }

    public String probe() throws IOException {
        String auth = authorization();
        String name = "ndg-probe-" + UUID.randomUUID() + ".zip";
        byte[] original = probeArchive("NormalDiscGolf WebDAV probe. Synthetic test data.\n");
        byte[] replacement = probeArchive("NormalDiscGolf WebDAV probe. Conditional write test.\n");
        var expected = new Checksum(original.length,
                HexFormat.of().formatHex(CourseUpgradeCheck.sha256().digest(original)));
        try {
            mirror(name, auth, HttpRequest.BodyPublishers.ofByteArray(original), expected);
            int response = put(name, auth, HttpRequest.BodyPublishers.ofByteArray(replacement), "application/zip");
            if (response != 412)
                throw new IOException("The destination accepted an overwrite. Expected HTTP 412 and received " + response + ".");
            verifyBytes(name, auth, expected, null);
            return name;
        } catch (IOException failure) {
            throw new IOException("Off-server probe failed for " + name + ". " + failure.getMessage(), failure);
        }
    }

    private static byte[] probeArchive(String content) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("ndg-webdav-probe.txt"));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private String mirror(String filename, String auth, HttpRequest.BodyPublisher body, Checksum expected) throws IOException {
        put(filename, auth, body, "application/zip");
        verifyBytes(filename, auth, expected, null);
        put(filename + ".sha256", auth, HttpRequest.BodyPublishers.ofString(expected + "\n"), "text/plain");
        if (!expected.equals(remoteChecksum(filename, auth))) throw new IOException("The off-server checksum differs from the local ZIP.");
        return expected.sha256();
    }

    public String verify(String filename) throws IOException {
        String auth = authorization();
        var expected = remoteChecksum(filename, auth);
        if (!expected.equals(checksum(local.verifiedArchive(filename))))
            throw new IOException("The off-server checksum differs from the local ZIP.");
        verifyBytes(filename, auth, expected, null);
        return expected.sha256();
    }

    public String download(String filename) throws IOException {
        String auth = authorization();
        Path temp = local.downloadTemp(filename);
        try {
            var expected = remoteChecksum(filename, auth);
            try (var out = Files.newOutputStream(temp)) {
                verifyBytes(filename, auth, expected, out);
            }
            local.importDownload(filename, temp);
            return expected.sha256();
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private String authorization() throws IOException {
        if (!enabled()) throw new IOException("Set backups.offsite.url and apply the settings first.");
        if (authorizationFile != null) return fileAuthorization();
        if (!authorizationEnv.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IOException("Set backups.offsite.authorization-env to a valid environment variable name.");
        String value = System.getenv(authorizationEnv);
        if (value == null || !value.matches("(?i)(Basic|Bearer) [!-~]+"))
            throw new IOException("Set the " + authorizationEnv + " environment variable to a Basic or Bearer authorization value.");
        return value;
    }

    private String fileAuthorization() throws IOException {
        if (!Files.isRegularFile(authorizationFile, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Create plugins/NormalDiscGolf/offsite-authorization.txt with the off-server credentials.");
        String text;
        try (var in = Files.newInputStream(authorizationFile, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes(AUTHORIZATION_LIMIT + 1);
            if (bytes.length > AUTHORIZATION_LIMIT) throw new IOException("The off-server credential file is too large.");
            try {
                text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            } catch (java.nio.charset.CharacterCodingException invalid) {
                throw new IOException("Save offsite-authorization.txt as UTF-8 plain text.", invalid);
            }
        }
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        text = text.strip();
        if (text.matches("(?i)(Basic|Bearer) [!-~]+")) return text;
        var lines = text.lines().toList();
        if (lines.size() != 2)
            throw new IOException("Use one Basic or Bearer header, or put the username on line 1 and the app password on line 2.");
        String username = lines.get(0).strip();
        String password = lines.get(1).strip();
        if (username.isEmpty() || username.contains(":") || username.codePoints().anyMatch(Character::isISOControl))
            throw new IOException("Check the WebDAV username on line 1 of offsite-authorization.txt.");
        if (password.isEmpty() || password.codePoints().anyMatch(Character::isISOControl))
            throw new IOException("Check the WebDAV app password on line 2 of offsite-authorization.txt.");
        return "Basic " + java.util.Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private record Checksum(long bytes, String sha256) {
        @Override public String toString() { return bytes + ":" + sha256; }
        static Checksum parse(String text) throws IOException {
            if (text.matches("[0-9]{1,19}:[0-9a-f]{64}")) {
                int colon = text.indexOf(':');
                try {
                    return new Checksum(Long.parseLong(text.substring(0, colon)), text.substring(colon + 1));
                } catch (NumberFormatException invalid) { }
            }
            throw new IOException("The off-server checksum file is invalid.");
        }
    }

    private static Checksum checksum(Path file) throws IOException {
        try (var in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            return Checksum.parse(DataBackups.transfer(in, null, Files.size(file)));
        }
    }

    private Checksum remoteChecksum(String filename, String auth) throws IOException {
        if (!DataBackups.archiveName(filename)) throw new IOException("Enter a backup ZIP filename.");
        var sidecar = new ByteArrayOutputStream();
        get(filename + ".sha256", auth, CHECKSUM_LIMIT, sidecar);
        return Checksum.parse(sidecar.toString(StandardCharsets.US_ASCII).strip());
    }

    private void verifyBytes(String filename, String auth, Checksum expected, OutputStream out) throws IOException {
        if (!expected.equals(get(filename, auth, expected.bytes(), out)))
            throw new IOException("The downloaded ZIP failed SHA-256 verification.");
    }

    private HttpRequest.Builder request(String name, String auth) {
        return HttpRequest.newBuilder(base.resolve(name)).timeout(TIMEOUT)
                .header("Authorization", auth).header("Accept-Encoding", "identity");
    }

    private int put(String name, String auth, HttpRequest.BodyPublisher payload, String type) throws IOException {
        var request = request(name, auth).header("Content-Type", type).header("If-None-Match", "*").PUT(payload).build();
        // A pre-existing object is accepted only after reading it back and checking the bytes.
        var result = send(request, info -> BodySubscribers.replacing(null));
        if (result.statusCode() / 100 != 2 && result.statusCode() != 412)
            throw new IOException("Off-server PUT returned HTTP " + result.statusCode() + ".");
        return result.statusCode();
    }

    private Checksum get(String name, String auth, long limit, OutputStream out) throws IOException {
        var response = send(request(name, auth).GET().build(), info -> info.statusCode() == 200
                ? new Receiver(limit, out)
                : BodySubscribers.replacing(null));
        if (response.statusCode() != 200) throw new IOException("Off-server GET returned HTTP " + response.statusCode() + ".");
        return response.body();
    }

    private static <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        var pending = HTTP.sendAsync(request, handler);
        try {
            return pending.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            pending.cancel(true);
            throw new IOException("The off-server request timed out.", timeout);
        } catch (InterruptedException canceled) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("The off-server request was interrupted.", canceled);
        } catch (ExecutionException failed) {
            throw new IOException("The off-server request failed. Check HTTPS connectivity and response size.", failed);
        }
    }

    private static final class Receiver implements HttpResponse.BodySubscriber<Checksum> {
        private final java.security.MessageDigest sha256 = CourseUpgradeCheck.sha256();
        private final CompletableFuture<Checksum> result = new CompletableFuture<>();
        private final long limit;
        private final OutputStream out;
        private final byte[] buffer = new byte[16_384];
        private Flow.Subscription subscription;
        private long bytes;

        private Receiver(long limit, OutputStream out) {
            this.limit = limit;
            this.out = out;
        }

        @Override public CompletableFuture<Checksum> getBody() { return result; }

        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override public void onNext(List<ByteBuffer> chunks) {
            try {
                for (var chunk : chunks) {
                    if (chunk.remaining() > limit - bytes) throw new IOException("Off-server response exceeds the expected size.");
                    bytes += chunk.remaining();
                    while (chunk.hasRemaining()) {
                        int size = Math.min(chunk.remaining(), buffer.length);
                        chunk.get(buffer, 0, size);
                        sha256.update(buffer, 0, size);
                        if (out != null) out.write(buffer, 0, size);
                    }
                }
                subscription.request(1);
            } catch (IOException failure) {
                result.completeExceptionally(failure);
                subscription.cancel();
            }
        }

        @Override public void onError(Throwable cause) { result.completeExceptionally(cause); }
        @Override public void onComplete() { result.complete(new Checksum(bytes, HexFormat.of().formatHex(sha256.digest()))); }
    }
}
