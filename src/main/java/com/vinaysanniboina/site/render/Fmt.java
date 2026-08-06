package com.vinaysanniboina.site.render;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Small formatting helpers exposed to templates as {@code ${fmt.…}}.
 * Kept deliberately dumb so template authors never have to write logic.
 */
public final class Fmt {

    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    /** "May 2026" */
    public String date(LocalDate d) {
        return d == null ? "" : LONG.format(d);
    }

    /** "2026-05-01", for &lt;time datetime&gt; and sitemaps. */
    public String iso(LocalDate d) {
        return d == null ? "" : ISO.format(d);
    }

    /** "18.4 MB". Uses MB = 1024 kB, matching what Windows and macOS report. */
    public String bytes(Long size) {
        if (size == null || size <= 0) return "—";
        String[] units = {"B", "kB", "MB", "GB"};
        double v = size;
        int i = 0;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return (i == 0 ? String.format(Locale.UK, "%.0f", v) : String.format(Locale.UK, "%.1f", v)) + " " + units[i];
    }

    /** First 12 characters of a checksum, enough to eyeball, with the full value in a title attribute. */
    public String shortHash(String sha) {
        if (sha == null || sha.isBlank()) return "—";
        return sha.length() <= 12 ? sha : sha.substring(0, 12) + "…";
    }

    /** Turns "Formula Student" into "formula-student" for CSS classes and filter values. */
    public String slug(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.UK).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    /** Joins with commas and a final "and". */
    public String list(List<String> items) {
        if (items == null || items.isEmpty()) return "";
        if (items.size() == 1) return items.get(0);
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    /** Truncates on a word boundary, for meta descriptions. */
    public String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        if (t.length() <= max) return t;
        int cut = t.lastIndexOf(' ', max - 1);
        return t.substring(0, cut < 40 ? max - 1 : cut) + "…";
    }

    public boolean isSet(String s) {
        return s != null && !s.isBlank();
    }

    public boolean any(List<?> l) {
        return l != null && !l.isEmpty();
    }

    /** File extension in upper case: "step", "SLDPRT" -> "STEP", "SLDPRT". */
    public String ext(String path) {
        if (path == null) return "";
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toUpperCase(Locale.UK);
    }

    public String fileName(String path) {
        if (path == null) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
