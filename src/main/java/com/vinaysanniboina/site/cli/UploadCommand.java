package com.vinaysanniboina.site.cli;

import com.vinaysanniboina.site.content.ContentLoader;
import com.vinaysanniboina.site.model.Project;
import com.vinaysanniboina.site.model.Site;
import com.vinaysanniboina.site.model.UploadLock;
import picocli.CommandLine;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Pushes CAD and simulation binaries to Cloudflare R2 over the S3-compatible API.
 *
 * <p>Why this exists: a GitHub Pages repository has a 1 GB soft limit and a hard 100 MB per-file
 * limit, and git stores every version of a binary forever. A single SOLIDWORKS assembly plus an
 * ANSYS project archive will blow through that in a term. R2 charges nothing for egress, so the
 * downloads stay free no matter who grabs them.
 *
 * <p>Credentials come from the environment and are never written to disk:
 * <pre>
 *   R2_ACCOUNT_ID          the 32-char hex id from the Cloudflare dashboard
 *   R2_ACCESS_KEY_ID       from an R2 API token scoped to Object Read and Write
 *   R2_SECRET_ACCESS_KEY   shown once when the token is created
 *   R2_BUCKET              e.g. vinayrajusanniboina-files
 * </pre>
 *
 * <p>Unchanged files are skipped by comparing the local SHA-256 against
 * {@code content/uploads.yaml}, so re-running this is cheap and idempotent.
 */
@CommandLine.Command(
        name = "upload",
        description = "Upload new or changed files under files/ to Cloudflare R2 and record their checksums.")
public final class UploadCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-r", "--root"}, description = "Repository root (default: ${DEFAULT-VALUE}).")
    Path root = Path.of(".");

    @CommandLine.Parameters(arity = "0..*",
            description = "Specific files to upload. Default: every file referenced by a project's downloads.")
    List<Path> only = new ArrayList<>();

    @CommandLine.Option(names = "--all",
            description = "Upload everything under files/, not just files referenced by a project.")
    boolean all;

    @CommandLine.Option(names = "--dry-run", description = "Show what would happen, upload nothing.")
    boolean dryRun;

    @CommandLine.Option(names = "--prefix",
            description = "Key prefix inside the bucket (default: none; keys mirror the path under files/).")
    String prefix = "";

    @Override
    public Integer call() throws Exception {
        ContentLoader loader = new ContentLoader(root);
        Site site = loader.loadSite();
        UploadLock lock = loader.loadUploadLock();
        Map<String, UploadLock.Entry> entries = new LinkedHashMap<>(lock.fileMap());

        List<Path> candidates = candidates(loader);
        if (candidates.isEmpty()) {
            System.out.println("Nothing to upload. No project references a file under files/.");
            return 0;
        }

        // Work out what actually changed before touching the network.
        List<Path> changed = new ArrayList<>();
        int skipped = 0;
        for (Path p : candidates) {
            String rel = rel(p);
            String sha = sha256(p);
            UploadLock.Entry existing = entries.get(rel);
            if (existing != null && sha.equals(existing.sha256()) && Files.size(p) == existing.size()) {
                skipped++;
            } else {
                changed.add(p);
            }
        }

        System.out.printf("%d file(s) referenced, %d unchanged, %d to upload.%n",
                candidates.size(), skipped, changed.size());
        if (changed.isEmpty()) {
            System.out.println("Everything is already on R2.");
            return 0;
        }
        for (Path p : changed) {
            System.out.printf("  %-58s %10s%n", rel(p), human(Files.size(p)));
        }

        if (dryRun) {
            System.out.println("\n--dry-run: stopping before upload.");
            return 0;
        }

        String accountId = env("R2_ACCOUNT_ID");
        String accessKey = env("R2_ACCESS_KEY_ID");
        String secretKey = env("R2_SECRET_ACCESS_KEY");
        String bucket = env("R2_BUCKET");

        try (S3Client s3 = client(accountId, accessKey, secretKey)) {
            try {
                s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (NoSuchBucketException e) {
                throw new IllegalStateException("Bucket '" + bucket + "' does not exist in account "
                        + accountId + ". Create it in the Cloudflare dashboard under R2.");
            }

            long total = 0;
            for (Path p : changed) {
                String relPath = rel(p);
                String key = key(relPath);
                String sha = sha256(p);
                long size = Files.size(p);
                String type = contentType(p);

                System.out.printf("Uploading %s -> %s (%s) ... ", relPath, key, human(size));
                s3.putObject(PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .contentType(type)
                                // Long cache: the key changes when the content does, because the
                                // checksum shown on the page changes with it.
                                .cacheControl("public, max-age=31536000, immutable")
                                .contentDisposition("attachment; filename=\"" + p.getFileName() + "\"")
                                .metadata(Map.of("sha256", sha))
                                .build(),
                        RequestBody.fromFile(p));
                System.out.println("done");

                entries.put(relPath, new UploadLock.Entry(
                        key, size, sha, type, Instant.now().toString(), bucket));
                total += size;
            }

            loader.writeUploadLock(new UploadLock(entries));
            System.out.printf("%nUploaded %d file(s), %s total.%n", changed.size(), human(total));
            System.out.println("Recorded in content/uploads.yaml — commit that file.");
            String base = site.downloads() == null ? null : site.downloads().base();
            if (base == null || base.isBlank()) {
                System.out.println("\nNote: site.yaml has no downloads.public_base_url set, so the "
                        + "download links will be blank. Set it to your R2 custom domain, "
                        + "e.g. https://files.vinayrajusanniboina.com");
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------

    private S3Client client(String accountId, String accessKey, String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
                // R2 ignores the region but the SDK insists on one; "auto" is what Cloudflare document.
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        // R2 does not implement the checksum trailers newer SDKs send by default.
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    /** Files referenced by a project's downloads or viewer, or everything under files/ with --all. */
    private List<Path> candidates(ContentLoader loader) throws IOException {
        Set<Path> set = new LinkedHashSet<>();
        if (!only.isEmpty()) {
            for (Path p : only) {
                Path abs = p.isAbsolute() ? p : root.resolve(p);
                if (!Files.isRegularFile(abs)) {
                    throw new IllegalArgumentException("Not a file: " + p);
                }
                set.add(abs.normalize());
            }
            return new ArrayList<>(set);
        }
        if (all) {
            Path dir = root.resolve("files");
            if (Files.isDirectory(dir)) {
                try (var walk = Files.walk(dir)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> !p.getFileName().toString().equals(".gitkeep"))
                            .sorted()
                            .forEach(p -> set.add(p.normalize()));
                }
            }
            return new ArrayList<>(set);
        }
        for (Project project : loader.loadProjects()) {
            for (Project.Download d : project.downloadList()) {
                if (d.file() == null || d.file().isBlank()) continue;
                Path abs = root.resolve(d.file()).normalize();
                if (Files.isRegularFile(abs)) {
                    set.add(abs);
                } else {
                    System.out.println("  (missing locally, skipped) " + d.file());
                }
            }
            if (project.hasViewer() && !project.viewer().model().startsWith("http")) {
                Path abs = root.resolve(project.viewer().model()).normalize();
                if (Files.isRegularFile(abs)) set.add(abs);
            }
        }
        return new ArrayList<>(set);
    }

    /** Object key mirrors the path under files/, so files/wing/a.step becomes wing/a.step. */
    private String key(String relPath) {
        String k = relPath.startsWith("files/") ? relPath.substring("files/".length()) : relPath;
        if (prefix != null && !prefix.isBlank()) {
            String p = prefix.endsWith("/") ? prefix : prefix + "/";
            k = p + k;
        }
        return k;
    }

    private String rel(Path p) {
        return root.toAbsolutePath().normalize()
                .relativize(p.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    private static String sha256(Path p) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[1 << 16];
        try (InputStream in = Files.newInputStream(p)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Content types for engineering formats. Browsers have no idea what a .SLDPRT is; setting these
     * explicitly stops R2 serving them as text and stops the browser trying to render them.
     */
    static String contentType(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        String ext = dot < 0 ? "" : n.substring(dot + 1);
        return switch (ext) {
            case "step", "stp" -> "model/step";
            case "iges", "igs" -> "model/iges";
            case "stl" -> "model/stl";
            case "glb" -> "model/gltf-binary";
            case "gltf" -> "model/gltf+json";
            case "pdf" -> "application/pdf";
            case "zip", "wbpz", "sldprt", "sldasm", "slddrw", "cas", "dat", "cas.h5", "msh", "wbpj"
                    -> "application/octet-stream";
            case "csv" -> "text/csv";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "mp4" -> "video/mp4";
            default -> "application/octet-stream";
        };
    }

    private static String env(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("""
                    Environment variable %s is not set.

                    Set all four before running upload:
                      R2_ACCOUNT_ID          Cloudflare dashboard -> R2 -> Account ID
                      R2_ACCESS_KEY_ID       R2 -> Manage API Tokens -> Create (Object Read & Write)
                      R2_SECRET_ACCESS_KEY   shown once, at token creation
                      R2_BUCKET              e.g. vinayrajusanniboina-files

                    PowerShell:
                      $env:R2_ACCOUNT_ID = "..."
                    bash:
                      export R2_ACCOUNT_ID=...
                    """.formatted(name));
        }
        return v;
    }

    private static String human(long size) {
        String[] units = {"B", "kB", "MB", "GB"};
        double v = size;
        int i = 0;
        while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
        return (i == 0 ? String.format(Locale.UK, "%.0f", v) : String.format(Locale.UK, "%.1f", v)) + " " + units[i];
    }
}
