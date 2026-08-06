package com.vinaysanniboina.site.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/** Copies {@code assets/} into the output directory and derives a cache-busting build id. */
public final class Assets {

    /** Recursively copies {@code from} into {@code to}, skipping editor and OS junk. */
    public static int copyTree(Path from, Path to) {
        if (!Files.isDirectory(from)) return 0;
        int[] count = {0};
        try {
            Files.walkFileTree(from, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    String name = dir.getFileName().toString();
                    if (name.startsWith(".") && !name.equals(".")) return FileVisitResult.SKIP_SUBTREE;
                    Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String name = file.getFileName().toString();
                    if (name.equals("Thumbs.db") || name.equals(".DS_Store") || name.endsWith("~")) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path target = to.resolve(from.relativize(file).toString());
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                    count[0]++;
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy " + from + " to " + to, e);
        }
        return count[0];
    }

    /** Deletes a directory tree. Used to make every build a clean build. */
    public static void deleteTree(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> paths = new ArrayList<>(walk.toList());
            for (int i = paths.size() - 1; i >= 0; i--) {
                Files.deleteIfExists(paths.get(i));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not clean " + dir, e);
        }
    }

    /**
     * Eight hex characters derived from the CSS and JS content, appended as {@code ?v=…} so a
     * browser that has cached the old stylesheet picks up a new one immediately after deploy.
     */
    public static String buildId(Path assetsDir) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (Files.isDirectory(assetsDir)) {
                try (Stream<Path> walk = Files.walk(assetsDir)) {
                    List<Path> files = walk
                            .filter(Files::isRegularFile)
                            .filter(p -> {
                                String n = p.getFileName().toString();
                                return n.endsWith(".css") || n.endsWith(".js");
                            })
                            .sorted()
                            .toList();
                    for (Path p : files) {
                        digest.update(p.getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        digest.update(Files.readAllBytes(p));
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, 8);
        } catch (Exception e) {
            return "dev";
        }
    }

    private Assets() {}
}
