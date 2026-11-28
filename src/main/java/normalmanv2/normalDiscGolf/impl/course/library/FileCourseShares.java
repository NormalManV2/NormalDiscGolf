package normalmanv2.normalDiscGolf.impl.course.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

final class FileCourseShares {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 7;
    private static final Pattern CODE = Pattern.compile("[" + ALPHABET + "]{" + CODE_LENGTH + "}");
    private static final String EXTENSION = ".share";
    private final Path directory;
    private final Logger logger;
    private final SecureRandom random = new SecureRandom();
    private Map<String, Share> shares;

    record Share(UUID owner, UUID course, boolean featured) {}

    FileCourseShares(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    Optional<String> findCode(UUID owner, UUID course) throws IOException {
        return entries().entrySet().stream()
                .filter(entry -> owner.equals(entry.getValue().owner()) && course.equals(entry.getValue().course()))
                .map(Map.Entry::getKey).findFirst();
    }

    String publish(UUID owner, UUID course) throws IOException {
        var current = findCode(owner, course);
        if (current.isPresent()) return current.get();
        String code;
        do {
            var candidate = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) candidate.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            code = candidate.toString();
        } while (shares.containsKey(code) || Files.exists(path(code)));
        write(code, new Share(owner, course, false));
        return code;
    }

    boolean revoke(UUID owner, UUID course) throws IOException {
        var code = findCode(owner, course);
        if (code.isEmpty()) return false;
        Files.deleteIfExists(path(code.get()));
        shares.remove(code.get());
        return true;
    }

    Share resolve(String input) throws IOException {
        String code = normalize(input);
        return CODE.matcher(code).matches() ? entries().get(code) : null;
    }

    List<String> featuredCodes() throws IOException {
        return entries().entrySet().stream().filter(entry -> entry.getValue().featured()).map(Map.Entry::getKey).toList();
    }

    void setFeatured(String code, Share share, boolean featured) throws IOException {
        write(normalize(code), new Share(share.owner(), share.course(), featured));
    }

    private static String normalize(String input) { return input.toUpperCase(Locale.ROOT); }

    private void write(String code, Share share) throws IOException {
        // Pin status belongs to the share so revocation removes both in a single operation.
        String data = share.owner() + " " + share.course() + (share.featured() ? " featured" : "") + "\n";
        FileCourseLibraryStore.writeAtomically(path(code), data);
        shares.put(code, share);
    }

    private Path path(String code) { return directory.resolve(code + EXTENSION); }

    private Map<String, Share> entries() throws IOException {
        if (shares != null) return shares;
        var loaded = new HashMap<String, Share>();
        if (!Files.notExists(directory)) {
            try (var paths = Files.list(directory)) {
                for (var file : paths.filter(path -> path.getFileName().toString().endsWith(EXTENSION)).sorted().toList()) {
                    String name = file.getFileName().toString();
                    String code = name.substring(0, name.length() - EXTENSION.length());
                    if (!CODE.matcher(code).matches()) continue;
                    try {
                        String[] ids = Files.readString(file).strip().split(" ");
                        if (ids.length != 2 && (ids.length != 3 || !ids[2].equals("featured"))) {
                            throw new IllegalArgumentException("Invalid share entry");
                        }
                        loaded.put(code, new Share(UUID.fromString(ids[0]), UUID.fromString(ids[1]), ids.length == 3));
                    } catch (IOException | IllegalArgumentException invalid) {
                        logger.warning("Could not load Course Library share " + name + ": " + invalid.getMessage());
                    }
                }
            }
        }
        shares = loaded;
        return shares;
    }
}
