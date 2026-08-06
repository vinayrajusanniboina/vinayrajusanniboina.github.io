package com.vinaysanniboina.site.gen;

import com.vinaysanniboina.site.model.Project;
import com.vinaysanniboina.site.model.Site;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** sitemap.xml, robots.txt and an optional RSS feed. All hand-written — no dependency needed. */
public final class Feeds {

    private static final DateTimeFormatter RFC_822 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.UK);

    private final Site site;

    public Feeds(Site site) {
        this.site = site;
    }

    public void writeSitemap(Path out, List<Project> projects, List<String> staticPaths) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        String today = LocalDate.now().toString();
        for (String path : staticPaths) {
            entry(sb, site.baseUrl() + path, today, path.equals("/") ? "1.0" : "0.8");
        }
        for (Project p : projects) {
            entry(sb, site.baseUrl() + p.url(),
                    p.date() == null ? today : p.date().toString(),
                    p.featured() ? "0.9" : "0.7");
        }
        sb.append("</urlset>\n");
        write(out, sb.toString());
    }

    private void entry(StringBuilder sb, String loc, String lastmod, String priority) {
        sb.append("  <url>\n")
          .append("    <loc>").append(escape(loc)).append("</loc>\n")
          .append("    <lastmod>").append(lastmod).append("</lastmod>\n")
          .append("    <priority>").append(priority).append("</priority>\n")
          .append("  </url>\n");
    }

    public void writeRobots(Path out) {
        String body = """
                User-agent: *
                Allow: /

                Sitemap: %s/sitemap.xml
                """.formatted(site.baseUrl());
        write(out, body);
    }

    /** RSS is optional; it costs nothing and lets anyone follow new project write-ups. */
    public void writeRss(Path out, List<Project> projects) {
        StringBuilder sb = new StringBuilder();
        String now = RFC_822.format(java.time.OffsetDateTime.now(ZoneOffset.UTC));
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\">\n")
          .append("<channel>\n")
          .append("  <title>").append(escape(site.title())).append("</title>\n")
          .append("  <link>").append(escape(site.baseUrl())).append("/</link>\n")
          .append("  <description>").append(escape(site.description())).append("</description>\n")
          .append("  <language>").append(site.lang() == null ? "en-GB" : site.lang()).append("</language>\n")
          .append("  <lastBuildDate>").append(now).append("</lastBuildDate>\n")
          .append("  <atom:link href=\"").append(escape(site.baseUrl()))
          .append("/feed.xml\" rel=\"self\" type=\"application/rss+xml\"/>\n");
        for (Project p : projects) {
            String url = site.baseUrl() + p.url();
            sb.append("  <item>\n")
              .append("    <title>").append(escape(p.title())).append("</title>\n")
              .append("    <link>").append(escape(url)).append("</link>\n")
              .append("    <guid isPermaLink=\"true\">").append(escape(url)).append("</guid>\n")
              .append("    <description>").append(escape(p.summary())).append("</description>\n");
            if (p.date() != null) {
                sb.append("    <pubDate>")
                  .append(RFC_822.format(p.date().atStartOfDay().atOffset(ZoneOffset.UTC)))
                  .append("</pubDate>\n");
            }
            for (String tag : p.tagList()) {
                sb.append("    <category>").append(escape(tag)).append("</category>\n");
            }
            sb.append("  </item>\n");
        }
        sb.append("</channel>\n</rss>\n");
        write(out, sb.toString());
    }

    /** GitHub Pages needs this to stop Jekyll from eating files and folders that begin with an underscore. */
    public void writeNoJekyll(Path out) {
        write(out, "");
    }

    /** The custom domain, read from site.yaml so the domain is configured in exactly one place. */
    public void writeCname(Path out) {
        write(out, site.host() + "\n");
    }

    private void write(Path out, String body) {
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, body);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + out + ": " + e.getMessage(), e);
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
