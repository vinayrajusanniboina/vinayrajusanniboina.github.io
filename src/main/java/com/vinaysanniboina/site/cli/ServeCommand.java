package com.vinaysanniboina.site.cli;

import com.sun.net.httpserver.HttpServer;
import com.vinaysanniboina.site.build.SiteBuilder;
import picocli.CommandLine;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/**
 * Local preview server.
 *
 * <p>Needed because the site uses root-relative URLs ({@code /assets/css/site.css}), which do not
 * resolve when you open {@code docs/index.html} straight off the disk with {@code file://}.
 * This serves the output directory the same way GitHub Pages will, including the 404 page and
 * directory-index resolution.
 */
@CommandLine.Command(
        name = "serve",
        description = "Build the site and serve it on http://localhost:8080 for local preview.")
public final class ServeCommand implements Callable<Integer> {

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("xml", "application/xml; charset=utf-8"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("avif", "image/avif"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("stl", "model/stl"),
            Map.entry("glb", "model/gltf-binary"),
            Map.entry("gltf", "model/gltf+json"));

    @CommandLine.Option(names = {"-r", "--root"}, description = "Repository root (default: ${DEFAULT-VALUE}).")
    Path root = Path.of(".");

    @CommandLine.Option(names = {"-o", "--out"}, description = "Output directory (default: ${DEFAULT-VALUE}).")
    Path out = Path.of("docs");

    @CommandLine.Option(names = {"-p", "--port"}, description = "Port (default: ${DEFAULT-VALUE}).")
    int port = 8080;

    @CommandLine.Option(names = "--no-build", description = "Serve what is already in the output directory.")
    boolean noBuild;

    @Override
    public Integer call() throws IOException {
        Path dir = root.resolve(out).toAbsolutePath().normalize();
        if (!noBuild) {
            SiteBuilder.Result r = new SiteBuilder(root, dir, false).build();
            r.warnings().forEach(w -> System.out.println("warn  " + w));
            System.out.printf("Built %d pages.%n", r.pages());
        }

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        } catch (java.net.BindException e) {
            // The default message is "Address already in use: bind", which tells you
            // nothing about what to do next.
            throw new IllegalStateException(
                    "Port " + port + " is already in use by another program.\n"
                            + "  Pick a different one:  java -jar target/site-builder.jar serve --port "
                            + (port + 1) + "\n"
                            + "  Or find out what has it:\n"
                            + "    Windows:  Get-NetTCPConnection -LocalPort " + port + " -State Listen\n"
                            + "    macOS/Linux:  lsof -i :" + port);
        }
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/", exchange -> {
            String raw = exchange.getRequestURI().getPath();
            String path = URLDecoder.decode(raw, StandardCharsets.UTF_8);
            Path file = resolve(dir, path);

            byte[] body;
            int status = 200;
            if (file == null) {
                Path notFound = dir.resolve("404.html");
                body = Files.exists(notFound) ? Files.readAllBytes(notFound)
                        : "404 Not Found".getBytes(StandardCharsets.UTF_8);
                status = 404;
                file = notFound;
            } else {
                body = Files.readAllBytes(file);
            }
            exchange.getResponseHeaders().add("Content-Type", contentType(file));
            exchange.getResponseHeaders().add("Cache-Control", "no-store");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        System.out.println();
        System.out.println("Serving " + dir);
        System.out.println("  http://localhost:" + port + "/");
        System.out.println("Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));

        // Block. The HTTP server runs on its own threads, so without this the
        // command would return, main() would exit, and the server would die
        // roughly a second after starting.
        try {
            new java.util.concurrent.CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return 0;
    }

    /** Maps a URL path onto a file, adding index.html for directories. Refuses to escape the root. */
    private static Path resolve(Path dir, String path) {
        String clean = path.replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);
        Path candidate = dir.resolve(clean).normalize();
        if (!candidate.startsWith(dir)) return null;
        if (Files.isDirectory(candidate)) {
            Path index = candidate.resolve("index.html");
            return Files.isRegularFile(index) ? index : null;
        }
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1);
        return TYPES.getOrDefault(ext, "application/octet-stream");
    }
}
