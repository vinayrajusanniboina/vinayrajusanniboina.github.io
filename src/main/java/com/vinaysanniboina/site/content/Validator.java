package com.vinaysanniboina.site.content;

import com.vinaysanniboina.site.model.About;
import com.vinaysanniboina.site.model.Project;
import com.vinaysanniboina.site.model.Site;
import com.vinaysanniboina.site.model.UploadLock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Schema and reference checking. This is the safety net that lets a non-web-developer add a project
 * without breaking the site.
 *
 * <p>Two severities:
 * <ul>
 *   <li><b>ERROR</b> — fails the build and the CI job. Missing required field, duplicate slug,
 *       image or download file that does not exist, download with no source, malformed URL.
 *   <li><b>WARN</b> — printed, build continues. Missing alt text on a decorative-looking image,
 *       thin project pages, a CV PDF that has not been dropped in yet.
 * </ul>
 */
public final class Validator {

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Set<String> KNOWN_TAGS = Set.of(
            "CAD", "CFD", "FEA", "EV Systems", "Formula Student",
            "Vehicle Dynamics", "Aerodynamics", "Manufacturing", "Testing", "Control Systems");

    private final Path root;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public Validator(Path root) {
        this.root = root;
    }

    public List<String> errors() { return errors; }
    public List<String> warnings() { return warnings; }
    public boolean ok() { return errors.isEmpty(); }

    public void validate(Site site, About about, List<Project> projects, UploadLock lock) {
        validateSite(site);
        validateAbout(about, projects);

        Set<String> slugs = new HashSet<>();
        for (Project p : projects) {
            validateProject(p, slugs, lock);
        }
    }

    // ------------------------------------------------------------------

    private void validateSite(Site site) {
        String where = "content/site.yaml";
        require(site.url(), where, "url");
        require(site.title(), where, "title");
        require(site.description(), where, "description");
        if (site.url() != null && !site.url().startsWith("https://")) {
            errors.add(where + ": url must start with https:// (got '" + site.url() + "')");
        }
        if (site.person() == null) {
            errors.add(where + ": missing 'person' block");
            return;
        }
        Site.Person p = site.person();
        require(p.name(), where, "person.name");
        require(p.title(), where, "person.title");
        require(p.email(), where, "person.email");
        require(p.location(), where, "person.location");
        if (p.email() != null && !p.email().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            errors.add(where + ": person.email does not look like an email address");
        }
        if (p.linkedin() != null) checkUrl(p.linkedin(), where, "person.linkedin");
        if (p.image() != null) {
            checkFile(p.image(), where, "person.image");
            if (isBlank(p.imageAlt())) {
                warnings.add(where + ": person.image has no image_alt");
            }
        }
        if (site.cv() != null && site.cv().pdf() != null && !Files.exists(root.resolve(site.cv().pdf()))) {
            warnings.add(where + ": cv.pdf points at " + site.cv().pdf()
                    + " which does not exist yet. The CV page will render but the download link will 404. "
                    + "Drop the PDF in at that path before deploying.");
        }
        if (site.downloads() != null && site.downloads().publicBaseUrl() != null) {
            checkUrl(site.downloads().publicBaseUrl(), where, "downloads.public_base_url");
        }
        for (Site.NavItem n : site.navList()) {
            require(n.label(), where, "nav[].label");
            require(n.href(), where, "nav[].href");
        }
    }

    private void validateAbout(About about, List<Project> projects) {
        String where = "content/about.yaml";
        require(about.profile(), where, "profile");
        if (about.educationList().isEmpty()) {
            warnings.add(where + ": no education entries");
        }
        for (About.Education e : about.educationList()) {
            require(e.qualification(), where, "education[].qualification");
            require(e.institution(), where, "education[].institution");
            require(e.dates(), where, "education[].dates");
        }
        for (About.SkillGroup g : about.skillList()) {
            require(g.group(), where, "skills[].group");
            for (About.SkillGroup.Skill s : g.itemList()) {
                require(s.name(), where, "skills[].items[].name");
                if (s.level() != null && (s.level() < 1 || s.level() > 5)) {
                    errors.add(where + ": skill '" + s.name() + "' has level " + s.level()
                            + "; must be 1-5 or omitted");
                }
            }
        }
        Set<String> slugs = new HashSet<>();
        projects.forEach(p -> slugs.add(p.slug()));
        for (String slug : about.cvProjectList()) {
            if (!slugs.contains(slug)) {
                errors.add(where + ": cv_projects references '" + slug + "' but there is no "
                        + "content/projects/" + slug + ".yaml");
            }
        }
    }

    private void validateProject(Project p, Set<String> seenSlugs, UploadLock lock) {
        String where = "content/projects/" + p.slug() + ".yaml";

        require(p.title(), where, "title");
        require(p.summary(), where, "summary");

        if (!SLUG.matcher(p.slug()).matches()) {
            errors.add(where + ": slug '" + p.slug() + "' must be lowercase letters, digits and "
                    + "single hyphens (it becomes the page URL)");
        }
        if (!seenSlugs.add(p.slug())) {
            errors.add(where + ": duplicate slug '" + p.slug() + "'");
        }
        if (p.summary() != null && p.summary().length() > 320) {
            warnings.add(where + ": summary is " + p.summary().length() + " characters; "
                    + "cards and meta descriptions read better under 320");
        }
        if (p.tagList().isEmpty()) {
            warnings.add(where + ": no tags, so this project will not appear under any index filter");
        }
        for (String tag : p.tagList()) {
            if (!KNOWN_TAGS.contains(tag)) {
                warnings.add(where + ": tag '" + tag + "' is new. That is allowed — it just adds a "
                        + "filter button. Known tags: " + String.join(", ", KNOWN_TAGS.stream().sorted().toList()));
            }
        }
        if (p.date() == null) {
            warnings.add(where + ": no date, so this project sorts last on the index");
        }

        // Images ------------------------------------------------------
        for (Project.Image img : p.allImages()) {
            if (img == null) continue;
            checkFile(img.src(), where, "image");
            if (isBlank(img.alt())) {
                errors.add(where + ": image '" + img.src() + "' has no alt text. Describe what an "
                        + "engineer should see in it — this is an accessibility requirement, not a nicety.");
            }
        }
        if (p.hero() == null) {
            warnings.add(where + ": no hero image; the card and the social preview will fall back to text");
        }

        // Sections ----------------------------------------------------
        if (isBlank(p.overview()) && isBlank(p.objective())) {
            warnings.add(where + ": neither 'objective' nor 'overview' is set; the page opens with the summary only");
        }
        for (Project.Decision d : p.decisionList()) {
            require(d.title(), where, "decisions[].title");
            require(d.decision(), where, "decisions[].decision");
            if (d.alternativeList().isEmpty() && isBlank(d.rationale())) {
                warnings.add(where + ": decision '" + d.title() + "' lists no alternatives and gives no "
                        + "rationale. A decision without a rejected alternative reads as an assertion.");
            }
            for (Project.Decision.Alternative a : d.alternativeList()) {
                require(a.option(), where, "decisions[].alternatives[].option");
                if (isBlank(a.whyNot())) {
                    warnings.add(where + ": alternative '" + a.option() + "' has no why_not");
                }
            }
        }
        for (Project.Calculation c : p.calculationList()) {
            require(c.title(), where, "calculations[].title");
            if (c.assumptionList().isEmpty()) {
                warnings.add(where + ": calculation '" + c.title() + "' states no assumptions");
            }
            for (Project.Calculation.Symbol s : c.symbolList()) {
                require(s.symbol(), where, "calculations[].symbols[].symbol");
                require(s.meaning(), where, "calculations[].symbols[].meaning");
                if (s.value() != null && isBlank(s.unit())) {
                    warnings.add(where + ": symbol '" + s.symbol() + "' has a value but no unit");
                }
            }
        }
        if (p.simulation() != null) {
            Project.Simulation s = p.simulation();
            if (isBlank(s.software())) warnings.add(where + ": simulation block has no 'software'");
            if (s.mesh() != null && isBlank(s.mesh().independence())) {
                warnings.add(where + ": mesh block has no independence study. Reviewers look for this first.");
            }
            checkTable(s.resultsTable(), where, "simulation.results_table");
            if (s.mesh() != null) checkTable(s.mesh().independenceTable(), where, "simulation.mesh.independence_table");
        }
        if (p.validation() != null) {
            checkTable(p.validation().resultsTable(), where, "validation.results_table");
        }

        // Downloads ---------------------------------------------------
        boolean anyNeutral = false;
        boolean anyProprietary = false;
        for (Project.Download d : p.downloadList()) {
            require(d.label(), where, "downloads[].label");
            if (isBlank(d.file()) && isBlank(d.url())) {
                errors.add(where + ": download '" + d.label() + "' has neither 'file' nor 'url'");
            }
            if (!isBlank(d.url())) checkUrl(d.url(), where, "downloads[].url");
            if (!isBlank(d.file())) {
                boolean uploaded = lock.fileMap().containsKey(normalise(d.file()));
                boolean present = Files.exists(root.resolve(d.file()));
                if (!uploaded && !present) {
                    errors.add(where + ": download '" + d.label() + "' points at " + d.file()
                            + " which is neither on disk nor in content/uploads.yaml. "
                            + "Either add the file and run `upload`, or remove the entry.");
                } else if (!uploaded) {
                    errors.add(where + ": download '" + d.label() + "' (" + d.file() + ") exists locally "
                            + "but has not been uploaded. Run:  java -jar target/site-builder.jar upload");
                }
            }
            if (d.neutralFormat()) anyNeutral = true; else anyProprietary = true;
            if (isBlank(d.requires())) {
                warnings.add(where + ": download '" + d.label() + "' does not say which software version "
                        + "is needed to open it");
            }
        }
        if (anyProprietary && !anyNeutral) {
            warnings.add(where + ": every download is in a proprietary format. Add a STEP, STL or PDF "
                    + "alternative so a reviewer without a SOLIDWORKS or ANSYS licence can still look.");
        }

        // Viewer ------------------------------------------------------
        if (p.viewer() != null && p.hasViewer()) {
            String m = p.viewer().model();
            if (!m.startsWith("http")) checkFile(m, where, "viewer.model");
            if (isBlank(p.viewer().poster())) {
                warnings.add(where + ": viewer has no poster image. Without one there is no fallback "
                        + "for anyone whose browser blocks the 3D module.");
            } else {
                checkFile(p.viewer().poster(), where, "viewer.poster");
            }
        }

        for (Project.Link l : p.linkList()) {
            require(l.label(), where, "links[].label");
            checkUrl(l.url(), where, "links[].url");
        }
    }

    // ------------------------------------------------------------------

    private void checkTable(Project.Table t, String where, String field) {
        if (t == null) return;
        int cols = t.columnList().size();
        if (cols == 0) {
            errors.add(where + ": " + field + " has no columns");
            return;
        }
        for (int i = 0; i < t.rowList().size(); i++) {
            int got = t.rowList().get(i).size();
            if (got != cols) {
                errors.add(where + ": " + field + " row " + (i + 1) + " has " + got
                        + " cells but there are " + cols + " columns");
            }
        }
    }

    private void checkFile(String relPath, String where, String field) {
        if (isBlank(relPath)) {
            errors.add(where + ": " + field + " has no path");
            return;
        }
        if (relPath.startsWith("http://") || relPath.startsWith("https://")) return;
        Path p = root.resolve(relPath);
        if (!Files.exists(p)) {
            errors.add(where + ": " + field + " points at '" + relPath + "' which does not exist. "
                    + "Paths are relative to the repository root, e.g. assets/img/projects/my-project/hero.jpg");
        }
    }

    private void checkUrl(String url, String where, String field) {
        if (isBlank(url)) {
            errors.add(where + ": " + field + " is empty");
            return;
        }
        String u = url.toLowerCase(Locale.ROOT);
        boolean internal = url.startsWith("/");
        if (!internal && !u.startsWith("https://") && !u.startsWith("http://") && !u.startsWith("mailto:")) {
            errors.add(where + ": " + field + " = '" + url + "' is not a usable link. Use a full "
                    + "https:// URL, a mailto: address, or a site-relative path starting with /");
        }
        if (u.startsWith("http://")) {
            warnings.add(where + ": " + field + " uses http://, which browsers flag. Use https://");
        }
    }

    private void require(String value, String where, String field) {
        if (isBlank(value)) {
            errors.add(where + ": required field '" + field + "' is missing or empty");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String normalise(String p) {
        return p.replace('\\', '/');
    }
}
