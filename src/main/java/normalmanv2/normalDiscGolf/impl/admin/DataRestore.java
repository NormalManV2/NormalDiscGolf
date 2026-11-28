package normalmanv2.normalDiscGolf.impl.admin;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Applies a reviewed restore on startup, before NDG loads any of its stores. */
public final class DataRestore {
    private static final LinkOption NOFOLLOW = LinkOption.NOFOLLOW_LINKS;
    private final DataBackups backups;
    private final Path data;
    private final Path pending;
    private final Path journal;

    public DataRestore(Path data, DataBackups backups) {
        this.data = data.toAbsolutePath().normalize();
        this.backups = backups;
        this.pending = this.data.resolve("restore-pending.properties");
        this.journal = this.data.resolve("restore-in-progress.properties");
    }

    public void queue(String filename, String reviewed) throws IOException {
        if (pendingArchive() != null) throw new IOException("A restore is already scheduled. Cancel it before scheduling another.");
        reviewedPlan(filename, reviewed);
        var request = new Properties();
        request.setProperty("archive", filename);
        request.setProperty("preview", reviewed);
        write(pending, request);
    }

    public String pendingArchive() throws IOException {
        if (Files.notExists(pending, NOFOLLOW)) return null;
        String archive = read(pending).getProperty("archive");
        if (!DataBackups.archiveName(archive)) throw new IOException("Invalid pending restore archive.");
        return archive;
    }

    public boolean cancel() throws IOException {
        return Files.deleteIfExists(pending);
    }

    public void applyOnStartup(Logger log) throws IOException {
        if (!Files.notExists(journal, NOFOLLOW)) {
            rollback(log);
            // A crash may have interrupted the request before startup consumed it.
            Files.deleteIfExists(pending);
            return;
        }
        if (Files.notExists(pending, NOFOLLOW)) return;
        var request = read(pending);
        Files.delete(pending);
        String filename = request.getProperty("archive");
        try {
            apply(filename, reviewedPlan(filename, request.getProperty("preview")), log);
        } catch (IOException failure) {
            if (!Files.notExists(journal, NOFOLLOW)) {
                log.log(Level.WARNING, "Restore could not finish. Rolling back the interrupted changes.", failure);
                try {
                    rollback(log);
                } catch (IOException rollbackFailure) {
                    rollbackFailure.addSuppressed(failure);
                    throw rollbackFailure;
                }
            } else {
                log.warning("Restore canceled before changing data. " + failure.getMessage());
            }
        }
    }

    private DataBackups.RestorePlan reviewedPlan(String filename, String reviewed) throws IOException {
        var plan = backups.plan(filename);
        if (!plan.preview().fingerprint().equals(reviewed))
            throw new IOException("The restore changes differ from the reviewed preview. Open a fresh preview.");
        if (plan.changes().isEmpty()) throw new IOException("This backup has no changes to restore.");
        return plan;
    }

    private void apply(String source, DataBackups.RestorePlan plan, Logger log) throws IOException {
        var changes = new TreeMap<>(plan.changes());
        Path staged = Files.createTempDirectory(data.resolve("backups"), "restore-");
        try {
            var desired = new TreeMap<String, String>();
            changes.forEach((name, change) -> desired.put(name, change.after()));
            backups.stage(source, staged, desired);
            for (var entry : changes.entrySet()) {
                if (!Objects.equals(hash(destination(entry.getKey())), entry.getValue().before()))
                    throw new IOException("Restore target changed or conflicts with an existing file: " + entry.getKey());
            }
            String safety = backups.create().filename();
            var before = new TreeMap<String, String>();
            changes.forEach((name, change) -> {
                if (change.before() != null) before.put(name, change.before());
            });
            // Check that the safety archive actually holds the bytes the admin agreed to replace.
            backups.verifyFiles(safety, before);
            var recovery = new Properties();
            recovery.setProperty("safety", safety);
            changes.forEach((name, change) -> recovery.setProperty("file." + name,
                    (change.before() == null ? "-" : change.before()) + "|" + change.after()));
            write(journal, recovery);
            log.info("Pre-restore backup created: " + safety);
            for (var entry : changes.entrySet()) {
                String name = entry.getKey();
                var target = destination(name);
                if (!Objects.equals(hash(target), entry.getValue().before()))
                    throw new IOException("Restore target changed: " + name);
                install(staged.resolve(name), target);
            }
            Files.delete(journal);
            log.info("Restored " + changes.size() + " NDG files from " + source + ". Pre-restore backup: " + safety);
        } finally {
            clean(staged, log);
        }
    }

    private void rollback(Logger log) throws IOException {
        var recovery = read(journal);
        String safety = recovery.getProperty("safety");
        if (!DataBackups.archiveName(safety)) throw new IOException("Invalid restore recovery archive.");
        var undo = new TreeMap<String, String>();
        var remove = new ArrayList<Path>();
        boolean found = false;
        for (String key : recovery.stringPropertyNames()) {
            if (!key.startsWith("file.")) continue;
            String name = key.substring(5);
            String[] hashes = recovery.getProperty(key).split("\\|", -1);
            if (!DataBackups.allowed(name) || hashes.length != 2 || !hashes[1].matches("[0-9]+:[0-9a-f]{64}")
                    || !(hashes[0].equals("-") || hashes[0].matches("[0-9]+:[0-9a-f]{64}")))
                throw new IOException("Invalid restore recovery entry.");
            found = true;
            String before = hashes[0].equals("-") ? null : hashes[0];
            Path target = destination(name);
            String live = hash(target);
            if (Objects.equals(before, live)) continue;
            if (!hashes[1].equals(live)) throw new IOException("Restore recovery found unexpected data at " + name + ".");
            if (before == null) remove.add(target);
            else undo.put(name, before);
        }
        if (!found) throw new IOException("The restore recovery journal contains no changes.");
        Path staged = Files.createTempDirectory(data.resolve("backups"), "recovery-");
        try {
            backups.stage(safety, staged, undo);
            for (String name : undo.keySet()) install(staged.resolve(name), destination(name));
            for (Path target : remove) Files.delete(target);
            Files.delete(journal);
            log.warning("Rolled back the interrupted NDG restore. Pre-restore backup: " + safety);
        } finally {
            clean(staged, log);
        }
    }

    private Path destination(String name) throws IOException {
        Path current = data;
        String[] parts = name.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (!Files.notExists(current, NOFOLLOW) && !Files.isDirectory(current, NOFOLLOW))
                throw new IOException("Restore path has a non-directory parent: " + name);
            current = current.resolve(parts[i]);
        }
        return current;
    }

    private static String hash(Path target) throws IOException {
        if (Files.notExists(target, NOFOLLOW)) return null;
        if (!Files.isRegularFile(target, NOFOLLOW))
            throw new IOException("Restore target is not a regular file: " + target.getFileName());
        try (var in = Files.newInputStream(target, NOFOLLOW)) {
            return DataBackups.transfer(in, null, Files.size(target));
        }
    }

    private static void install(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "ndg-restore-", ".tmp");
        try {
            try (var in = Files.newInputStream(source); var out = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                in.transferTo(Channels.newOutputStream(out));
                out.force(true);
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Properties read(Path file) throws IOException {
        var values = new Properties();
        try (var in = Files.newInputStream(file, NOFOLLOW)) {
            byte[] bytes = in.readNBytes(DataBackups.MANIFEST_LIMIT + 1);
            if (bytes.length > DataBackups.MANIFEST_LIMIT) throw new IOException("Restore metadata is too large.");
            values.load(new java.io.ByteArrayInputStream(bytes));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid restore metadata.", invalid);
        }
        return values;
    }

    private static void write(Path file, Properties values) throws IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        values.store(bytes, "NormalDiscGolf restore");
        if (bytes.size() > DataBackups.MANIFEST_LIMIT) throw new IOException("Restore metadata is too large.");
        Path temp = Files.createTempFile(file.getParent(), "ndg-restore-", ".tmp");
        try {
            try (var out = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                bytes.writeTo(Channels.newOutputStream(out));
                out.force(true);
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void clean(Path staged, Logger log) {
        try (var files = Files.walk(staged)) {
            for (var path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException | java.io.UncheckedIOException failure) {
            log.warning("Could not remove temporary restore files at " + staged.getFileName() + ".");
        }
    }
}
