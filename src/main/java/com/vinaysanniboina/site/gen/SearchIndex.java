package com.vinaysanniboina.site.gen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinaysanniboina.site.model.Project;
import com.vinaysanniboina.site.render.Markdown;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes {@code /search-index.json}: one record per project, fetched once by the projects page and
 * searched in the browser.
 *
 * <p>With a portfolio of tens of projects this is a few tens of kB — no search server, no Lunr,
 * no build step in the browser. The blob includes a flattened "body" field so a search for
 * "engine mount" or "y+" hits the detail sections and not just the summary.
 */
public final class SearchIndex {

    private final ObjectMapper json = new ObjectMapper();
    private final Markdown md = new Markdown();

    public void write(Path out, List<Project> projects) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Project p : projects) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("slug", p.slug());
            r.put("title", p.title());
            r.put("subtitle", p.subtitle() == null ? "" : p.subtitle());
            r.put("summary", p.summary());
            r.put("url", p.url());
            r.put("tags", p.tagList());
            r.put("tools", p.toolList());
            r.put("date", p.date() == null ? "" : p.date().toString());
            r.put("year", p.date() == null ? "" : String.valueOf(p.date().getYear()));
            r.put("featured", p.featured());
            r.put("image", p.hero() == null ? "" : p.hero().src());
            r.put("alt", p.hero() == null ? "" : p.hero().alt());
            r.put("metrics", p.metricList().stream()
                    .map(m -> Map.of("value", nz(m.value()), "label", nz(m.label())))
                    .toList());
            r.put("body", body(p));
            records.add(r);
        }
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, json.writeValueAsString(records));
        } catch (IOException e) {
            throw new IllegalStateException("Could not write search index: " + e.getMessage(), e);
        }
    }

    /** Everything searchable, flattened to plain text and capped so the index stays small. */
    private String body(Project p) {
        StringBuilder sb = new StringBuilder();
        add(sb, p.objective());
        add(sb, p.overview());
        add(sb, p.role());
        add(sb, p.organisation());
        if (p.brief() != null) {
            add(sb, p.brief().context());
            p.brief().requirementList().forEach(r -> add(sb, r.text()));
            p.brief().constraintList().forEach(c -> add(sb, c));
        }
        for (Project.Decision d : p.decisionList()) {
            add(sb, d.title());
            add(sb, d.decision());
            add(sb, d.rationale());
            d.alternativeList().forEach(a -> { add(sb, a.option()); add(sb, a.whyNot()); });
        }
        add(sb, p.methodology());
        for (Project.Calculation c : p.calculationList()) {
            add(sb, c.title());
            add(sb, c.intro());
            add(sb, c.result());
        }
        if (p.simulation() != null) {
            Project.Simulation s = p.simulation();
            add(sb, s.software());
            add(sb, s.solver());
            add(sb, s.turbulenceModel());
            add(sb, s.results());
            add(sb, s.convergence());
            if (s.mesh() != null) {
                add(sb, s.mesh().type());
                add(sb, s.mesh().independence());
            }
        }
        if (p.validation() != null) {
            add(sb, p.validation().approach());
            add(sb, p.validation().results());
            p.validation().lessonList().forEach(l -> add(sb, l));
            p.validation().issueList().forEach(l -> add(sb, l));
        }
        String text = md.text(sb.toString());
        return text.length() > 6000 ? text.substring(0, 6000) : text;
    }

    private void add(StringBuilder sb, String s) {
        if (s != null && !s.isBlank()) sb.append(s).append("\n\n");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
