package com.vinaysanniboina.site.content;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vinaysanniboina.site.model.About;
import com.vinaysanniboina.site.model.Project;
import com.vinaysanniboina.site.model.Site;
import com.vinaysanniboina.site.model.UploadLock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads the {@code content/} directory into typed objects.
 *
 * <p>Two deliberate choices:
 * <ul>
 *   <li><b>snake_case keys.</b> YAML uses {@code turbulence_model}; Java uses {@code turbulenceModel}.
 *   <li><b>Unknown keys are fatal.</b> Writing {@code tubulence_model} by mistake fails the build with
 *       the file name and the offending key, instead of silently rendering an empty section.
 * </ul>
 */
public final class ContentLoader {

    private final ObjectMapper yaml = buildMapper();
    private final Path root;

    public ContentLoader(Path root) {
        this.root = root;
    }

    public static ObjectMapper buildMapper() {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .build();
        ObjectMapper m = new ObjectMapper(factory);
        m.registerModule(new JavaTimeModule());
        m.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        m.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        m.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return m;
    }

    public Path root() {
        return root;
    }

    public Path contentDir() {
        return root.resolve("content");
    }

    public Site loadSite() {
        return read(contentDir().resolve("site.yaml"), Site.class);
    }

    public About loadAbout() {
        return read(contentDir().resolve("about.yaml"), About.class);
    }

    public UploadLock loadUploadLock() {
        Path p = contentDir().resolve("uploads.yaml");
        if (!Files.exists(p)) return UploadLock.empty();
        UploadLock lock = read(p, UploadLock.class);
        return lock.files() == null ? UploadLock.empty() : lock;
    }

    public void writeUploadLock(UploadLock lock) {
        Path p = contentDir().resolve("uploads.yaml");
        try {
            String header = """
                    # Written by `upload`. Do not edit by hand.
                    # Maps a local file path to its Cloudflare R2 object, size and SHA-256 checksum.
                    # The generator reads this to fill in the download tables on project pages.
                    """;
            Files.writeString(p, header + yaml.writeValueAsString(lock));
        } catch (IOException e) {
            throw new ContentException("Could not write " + p + ": " + e.getMessage(), e);
        }
    }

    /**
     * Loads every {@code content/projects/*.yaml}. The slug defaults to the file name, so
     * {@code front-wing-cfd.yaml} becomes {@code /projects/front-wing-cfd/} unless the file
     * sets {@code slug:} explicitly.
     */
    public List<Project> loadProjects() {
        Path dir = contentDir().resolve("projects");
        if (!Files.isDirectory(dir)) {
            throw new ContentException("No content/projects directory found under " + root.toAbsolutePath());
        }
        List<Project> projects = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> yamlFiles = files
                    .filter(p -> p.getFileName().toString().endsWith(".yaml")
                            || p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();
            for (Path p : yamlFiles) {
                Project raw = read(p, Project.class);
                String fileSlug = p.getFileName().toString().replaceFirst("\\.ya?ml$", "");
                projects.add(withSlug(raw, raw.slug() == null || raw.slug().isBlank() ? fileSlug : raw.slug()));
            }
        } catch (IOException e) {
            throw new ContentException("Could not list " + dir + ": " + e.getMessage(), e);
        }
        projects.sort(projectOrder());
        return projects;
    }

    /** Featured first is handled on the home page; the index sorts by explicit order, then newest first. */
    public static Comparator<Project> projectOrder() {
        return Comparator
                .comparingInt((Project p) -> p.order() == null ? 500 : p.order())
                .thenComparing(p -> p.date() == null ? java.time.LocalDate.MIN : p.date(),
                        Comparator.reverseOrder())
                .thenComparing(Project::title);
    }

    private static Project withSlug(Project p, String slug) {
        return new Project(slug, p.title(), p.subtitle(), p.summary(), p.date(), p.status(), p.featured(),
                p.order(), p.tags(), p.tools(), p.role(), p.timeline(), p.organisation(), p.location(),
                p.hero(), p.metrics(), p.objective(), p.overview(), p.brief(), p.decisions(), p.methodology(),
                p.calculations(), p.simulation(), p.validation(), p.gallery(), p.downloads(), p.viewer(),
                p.links(), p.seo());
    }

    private <T> T read(Path file, Class<T> type) {
        if (!Files.exists(file)) {
            throw new ContentException("Missing content file: " + rel(file));
        }
        try {
            T value = yaml.readValue(file.toFile(), type);
            if (value == null) throw new ContentException(rel(file) + " is empty.");
            return value;
        } catch (UnrecognizedPropertyException e) {
            throw new ContentException(
                    rel(file) + ": unknown key '" + e.getPropertyName() + "' at line "
                            + (e.getLocation() == null ? "?" : e.getLocation().getLineNr())
                            + ".\n  Known keys here: " + String.join(", ", e.getKnownPropertyIds().stream()
                            .map(Object::toString).sorted().toList()), e);
        } catch (IOException e) {
            throw new ContentException(rel(file) + ": " + e.getMessage(), e);
        }
    }

    private String rel(Path p) {
        try {
            return root.toAbsolutePath().relativize(p.toAbsolutePath()).toString().replace('\\', '/');
        } catch (Exception e) {
            return p.toString();
        }
    }

    /** Thrown for anything that makes the content unreadable. Message is meant to be read by a human. */
    public static class ContentException extends RuntimeException {
        public ContentException(String message) { super(message); }
        public ContentException(String message, Throwable cause) { super(message, cause); }
    }
}
