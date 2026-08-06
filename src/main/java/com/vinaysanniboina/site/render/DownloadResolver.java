package com.vinaysanniboina.site.render;

import com.vinaysanniboina.site.model.Project;
import com.vinaysanniboina.site.model.Site;
import com.vinaysanniboina.site.model.UploadLock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Joins the hand-written {@code downloads:} entries in a project file to the machine-written
 * {@code content/uploads.yaml}, producing everything the download table needs.
 *
 * <p>Exposed to templates as {@code ${dl.of(project)}}.
 */
public final class DownloadResolver {

    private final Site site;
    private final UploadLock lock;

    public DownloadResolver(Site site, UploadLock lock) {
        this.site = site;
        this.lock = lock;
    }

    public List<Resolved> of(Project project) {
        List<Resolved> out = new ArrayList<>();
        for (Project.Download d : project.downloadList()) {
            out.add(resolve(d));
        }
        return out;
    }

    /** True when the project has at least one download in a format anyone can open. */
    public boolean hasNeutral(Project project) {
        return project.downloadList().stream().anyMatch(Project.Download::neutralFormat);
    }

    private Resolved resolve(Project.Download d) {
        String path = d.file() == null ? null : d.file().replace('\\', '/');
        UploadLock.Entry entry = path == null ? null : lock.fileMap().get(path);

        String href = d.url();
        if (href == null && entry != null) {
            String base = site.downloads() == null ? "" : site.downloads().base();
            href = base + "/" + entry.key();
        }

        Long size = d.sizeBytes() != null ? d.sizeBytes() : (entry == null ? null : entry.size());
        String sha = d.sha256() != null ? d.sha256() : (entry == null ? null : entry.sha256());
        String type = d.contentType() != null ? d.contentType() : (entry == null ? null : entry.contentType());

        String name = path != null ? fileName(path) : fileName(href == null ? "" : href);
        String format = d.format() != null ? d.format() : extension(name);

        return new Resolved(
                d.label(), href, name, format, size, sha, type,
                d.requires(), d.note(), d.neutralFormat(), href != null && !href.isBlank());
    }

    private static String fileName(String p) {
        int q = p.indexOf('?');
        String s = q < 0 ? p : p.substring(0, q);
        int slash = s.lastIndexOf('/');
        return slash < 0 ? s : s.substring(slash + 1);
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "FILE" : name.substring(dot + 1).toUpperCase(Locale.UK);
    }

    /** One row of the downloads table. */
    public record Resolved(
            String label,
            String href,
            String fileName,
            String format,
            Long size,
            String sha256,
            String contentType,
            String requires,
            String note,
            boolean neutralFormat,
            boolean available
    ) {}
}
