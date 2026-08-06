package com.vinaysanniboina.site.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One project = one YAML file in {@code content/projects/}.
 *
 * <p>Every field except {@code slug}, {@code title} and {@code summary} is optional. A project
 * page renders only the sections whose data is present, so a short write-up and a full
 * dissertation-length write-up both use the same template.
 *
 * <p>YAML keys are snake_case ({@code turbulence_model}); the Java components are camelCase.
 * Jackson is configured with SNAKE_CASE naming in {@code ContentLoader}, and unknown keys are a
 * hard error so a mistyped key fails the build instead of silently disappearing.
 */
public record Project(
        String slug,
        String title,
        String subtitle,
        String summary,
        LocalDate date,
        /** free text, e.g. "Complete", "In progress", "Ongoing to Jun 2026" */
        String status,
        boolean featured,
        /** lower sorts first on the index page; ties broken by date descending */
        Integer order,
        List<String> tags,
        List<String> tools,
        String role,
        String timeline,
        String organisation,
        String location,
        Image hero,
        List<Metric> metrics,

        // ---- the templated detail sections, all optional ----
        String objective,
        String overview,
        Brief brief,
        List<Decision> decisions,
        String methodology,
        List<Calculation> calculations,
        Simulation simulation,
        Validation validation,
        List<Image> gallery,
        List<Download> downloads,
        Viewer viewer,
        List<Link> links,
        Seo seo
) {

    /** Null-safe accessors used by templates so they never have to null-check a list. */
    public List<String> tagList() { return tags == null ? List.of() : tags; }
    public List<String> toolList() { return tools == null ? List.of() : tools; }
    public List<Metric> metricList() { return metrics == null ? List.of() : metrics; }
    public List<Decision> decisionList() { return decisions == null ? List.of() : decisions; }
    public List<Calculation> calculationList() { return calculations == null ? List.of() : calculations; }
    public List<Image> galleryList() { return gallery == null ? List.of() : gallery; }
    public List<Download> downloadList() { return downloads == null ? List.of() : downloads; }
    public List<Link> linkList() { return links == null ? List.of() : links; }

    public String url() { return "/projects/" + slug + "/"; }

    /** True when any calculation or simulation block contains TeX, so KaTeX is only loaded where needed. */
    public boolean needsMath() {
        for (Calculation c : calculationList()) {
            if (c.displayMath() != null && !c.displayMath().isBlank()) return true;
            for (Calculation.Step s : c.stepList()) {
                if (s.math() != null && !s.math().isBlank()) return true;
            }
            for (Calculation.Symbol s : c.symbolList()) {
                if (s.symbol() != null && s.symbol().contains("\\")) return true;
            }
        }
        return false;
    }

    public boolean hasViewer() { return viewer != null && viewer.model() != null && !viewer.model().isBlank(); }

    /** Every image the project references, used by the validator to check the files exist. */
    public List<Image> allImages() {
        List<Image> out = new ArrayList<>();
        if (hero != null) out.add(hero);
        out.addAll(galleryList());
        if (simulation != null) out.addAll(simulation.figureList());
        return out;
    }

    // ------------------------------------------------------------------
    // Nested value types
    // ------------------------------------------------------------------

    /** A picture. {@code src} is relative to the repo root, e.g. {@code assets/img/projects/x/hero.jpg}. */
    public record Image(String src, String alt, String caption, Integer width, Integer height) {}

    /** A headline number shown in the project header, e.g. value "~45%", label "drag reduction". */
    public record Metric(String value, String label, String note) {}

    /** Section 2: design brief, requirements and constraints. */
    public record Brief(
            String context,
            List<Requirement> requirements,
            List<String> constraints,
            List<String> outOfScope
    ) {
        public List<Requirement> requirementList() { return requirements == null ? List.of() : requirements; }
        public List<String> constraintList() { return constraints == null ? List.of() : constraints; }
        public List<String> outOfScopeList() { return outOfScope == null ? List.of() : outOfScope; }

        public record Requirement(String id, String text, String rationale, String verification) {}
    }

    /**
     * Section 3: design decisions. The template renders these as
     * decision -&gt; alternatives considered -&gt; why chosen -&gt; consequences.
     */
    public record Decision(
            String title,
            String decision,
            List<Alternative> alternatives,
            String rationale,
            String consequences,
            String evidence
    ) {
        public List<Alternative> alternativeList() { return alternatives == null ? List.of() : alternatives; }

        public record Alternative(String option, String whyNot) {}
    }

    /** Section 5: a worked calculation with stated assumptions, symbols and units. */
    public record Calculation(
            String title,
            String intro,
            List<String> assumptions,
            List<Symbol> symbols,
            /** a single headline equation rendered on its own line, TeX without delimiters */
            String displayMath,
            List<Step> steps,
            String result,
            String note
    ) {
        public List<String> assumptionList() { return assumptions == null ? List.of() : assumptions; }
        public List<Symbol> symbolList() { return symbols == null ? List.of() : symbols; }
        public List<Step> stepList() { return steps == null ? List.of() : steps; }

        /** A symbol in the nomenclature table. {@code symbol} is TeX, e.g. {@code \rho}. */
        public record Symbol(String symbol, String meaning, String value, String unit, String source) {}

        /** One line of working. {@code math} is TeX without delimiters. */
        public record Step(String description, String math, String result, String note) {}
    }

    /** Section 6: simulation setup and results. */
    public record Simulation(
            String software,
            String solver,
            String domain,
            String turbulenceModel,
            Mesh mesh,
            List<KeyValue> boundaryConditions,
            List<KeyValue> solverSettings,
            String convergence,
            String results,
            Table resultsTable,
            List<Image> figures,
            String note
    ) {
        public List<KeyValue> boundaryConditionList() { return boundaryConditions == null ? List.of() : boundaryConditions; }
        public List<KeyValue> solverSettingList() { return solverSettings == null ? List.of() : solverSettings; }
        public List<Image> figureList() { return figures == null ? List.of() : figures; }

        public record Mesh(
                String type,
                String elements,
                String nodes,
                String yPlus,
                String growthRate,
                String inflationLayers,
                String qualityMetric,
                /** markdown: the mesh independence study */
                String independence,
                Table independenceTable
        ) {}
    }

    /** Section 7: validation, testing, and what went wrong. */
    public record Validation(
            String approach,
            String results,
            Table resultsTable,
            List<String> issues,
            List<String> lessons,
            String futureWork
    ) {
        public List<String> issueList() { return issues == null ? List.of() : issues; }
        public List<String> lessonList() { return lessons == null ? List.of() : lessons; }
    }

    /**
     * Section 9: one downloadable file.
     *
     * <p>{@code file} is the path of the local source file relative to the repo root, e.g.
     * {@code files/front-wing/front-wing.step}. Size, checksum, content type and public URL are
     * NOT written here by hand — {@code upload} records them in {@code content/uploads.yaml} and
     * the generator joins them in. Set {@code url} explicitly only for files you host elsewhere.
     */
    public record Download(
            String label,
            String file,
            String url,
            String format,
            /** "SOLIDWORKS 2023 or newer", "ANSYS Workbench 2023 R2", "any CAD / free viewer" */
            String requires,
            String note,
            /** true for STEP/STL/PDF alternatives a reviewer can open without a licence */
            boolean neutralFormat,
            // ---- normally filled from uploads.yaml; overrides allowed ----
            Long sizeBytes,
            String sha256,
            String contentType
    ) {}

    /** Section 10: optional lazy-loaded 3D viewer. {@code model} is an STL or glTF/GLB URL or path. */
    public record Viewer(String model, String format, String poster, String posterAlt, String note) {}

    public record Link(String label, String url) {}

    public record KeyValue(String name, String value) {}

    /** A simple data table: column headers plus rows of cells. */
    public record Table(String caption, List<String> columns, List<List<String>> rows) {
        public List<String> columnList() { return columns == null ? List.of() : columns; }
        public List<List<String>> rowList() { return rows == null ? List.of() : rows; }
    }

    /** Per-page overrides for meta description and social share image. */
    public record Seo(String description, String image) {}
}
