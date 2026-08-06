package com.vinaysanniboina.site.build;

import com.vinaysanniboina.site.content.ContentLoader;
import com.vinaysanniboina.site.content.Validator;
import com.vinaysanniboina.site.gen.Feeds;
import com.vinaysanniboina.site.gen.JsonLd;
import com.vinaysanniboina.site.gen.SearchIndex;
import com.vinaysanniboina.site.model.About;
import com.vinaysanniboina.site.model.Project;
import com.vinaysanniboina.site.model.Site;
import com.vinaysanniboina.site.model.UploadLock;
import com.vinaysanniboina.site.render.DownloadResolver;
import com.vinaysanniboina.site.render.Fmt;
import com.vinaysanniboina.site.render.Markdown;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The build. Reads {@code content/}, validates it, renders {@code docs/}.
 *
 * <p>Output layout is directory-per-page ({@code /projects/front-wing-cfd/index.html}) so every URL
 * is clean and has a trailing slash, which keeps relative links and GitHub Pages happy.
 */
public final class SiteBuilder {

    private final Path root;
    private final Path out;
    private final boolean strict;

    public SiteBuilder(Path root, Path out, boolean strict) {
        this.root = root;
        this.out = out;
        this.strict = strict;
    }

    public record Result(int pages, int projects, int assets, List<String> warnings) {}

    public Result build() {
        ContentLoader loader = new ContentLoader(root);
        Site site = loader.loadSite();
        About about = loader.loadAbout();
        List<Project> projects = loader.loadProjects();
        UploadLock lock = loader.loadUploadLock();

        Validator validator = new Validator(root);
        validator.validate(site, about, projects, lock);

        if (!validator.errors().isEmpty()) {
            StringBuilder sb = new StringBuilder("Content validation failed:\n");
            validator.errors().forEach(e -> sb.append("  ERROR  ").append(e).append('\n'));
            validator.warnings().forEach(w -> sb.append("  warn   ").append(w).append('\n'));
            throw new ContentLoader.ContentException(sb.toString());
        }
        if (strict && !validator.warnings().isEmpty()) {
            StringBuilder sb = new StringBuilder("Warnings present and --strict is on:\n");
            validator.warnings().forEach(w -> sb.append("  warn   ").append(w).append('\n'));
            throw new ContentLoader.ContentException(sb.toString());
        }

        Assets.deleteTree(out);
        mkdirs(out);

        int assetCount = Assets.copyTree(root.resolve("assets"), out.resolve("assets"));

        TemplateEngine engine = engine();
        JsonLd jsonLd = new JsonLd(site, about);
        DownloadResolver downloads = new DownloadResolver(site, lock);
        Fmt fmt = new Fmt();
        Markdown md = new Markdown();
        String buildId = Assets.buildId(root.resolve("assets"));
        String builtAt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK).format(ZonedDateTime.now());

        List<Project> featured = featured(site, projects);
        Set<String> allTags = new LinkedHashSet<>();
        projects.forEach(p -> allTags.addAll(p.tagList()));

        int pages = 0;

        // ---- Home ----
        Context home = base(site, about, fmt, md, downloads, buildId, builtAt);
        home.setVariable("pageId", "home");
        home.setVariable("navCurrent", "/");
        // site.title() rather than name + title, which would double up the dash.
        home.setVariable("pageTitle", site.title());
        home.setVariable("pageDescription", site.description());
        home.setVariable("canonical", site.baseUrl() + "/");
        home.setVariable("jsonLd", jsonLd.website());
        home.setVariable("featured", featured);
        home.setVariable("projects", projects);
        write(engine, "index", home, out.resolve("index.html"));
        pages++;

        // ---- Projects index ----
        Context idx = base(site, about, fmt, md, downloads, buildId, builtAt);
        idx.setVariable("pageId", "projects");
        idx.setVariable("navCurrent", "/projects/");
        idx.setVariable("pageTitle", "Projects \u2014 " + site.person().name());
        idx.setVariable("pageDescription",
                "Engineering projects by " + site.person().name() + ": CFD, FEA, CAD, vehicle dynamics "
                        + "and EV systems, with methodology, calculations and downloadable models.");
        idx.setVariable("canonical", site.baseUrl() + "/projects/");
        idx.setVariable("jsonLd", jsonLd.person());
        idx.setVariable("projects", projects);
        idx.setVariable("tags", allTags);
        write(engine, "projects", idx, out.resolve("projects/index.html"));
        pages++;

        // ---- Project detail pages ----
        for (Project p : projects) {
            Context c = base(site, about, fmt, md, downloads, buildId, builtAt);
            c.setVariable("pageId", "project");
            c.setVariable("navCurrent", "/projects/");
            c.setVariable("project", p);
            c.setVariable("downloads", downloads.of(p));
            c.setVariable("pageTitle", p.title() + " \u2014 " + site.person().name());
            c.setVariable("pageDescription",
                    p.seo() != null && p.seo().description() != null
                            ? p.seo().description()
                            : fmt.truncate(p.summary(), 300));
            c.setVariable("canonical", site.baseUrl() + p.url());
            c.setVariable("ogImage", ogImage(site, p));
            c.setVariable("jsonLd", jsonLd.project(p));
            c.setVariable("needsMath", p.needsMath());
            c.setVariable("hasViewer", p.hasViewer());
            write(engine, "project", c, out.resolve("projects/" + p.slug() + "/index.html"));
            pages++;
        }

        // ---- About ----
        Context ab = base(site, about, fmt, md, downloads, buildId, builtAt);
        ab.setVariable("pageId", "about");
        ab.setVariable("navCurrent", "/about/");
        ab.setVariable("pageTitle", "About \u2014 " + site.person().name());
        ab.setVariable("pageDescription", fmt.truncate(md.text(about.profile()), 300));
        ab.setVariable("canonical", site.baseUrl() + "/about/");
        ab.setVariable("jsonLd", jsonLd.person());
        write(engine, "about", ab, out.resolve("about/index.html"));
        pages++;

        // ---- CV ----
        List<Project> cvProjects = new ArrayList<>();
        for (String slug : about.cvProjectList()) {
            projects.stream().filter(p -> p.slug().equals(slug)).findFirst().ifPresent(cvProjects::add);
        }
        if (cvProjects.isEmpty()) cvProjects.addAll(featured);
        Context cv = base(site, about, fmt, md, downloads, buildId, builtAt);
        cv.setVariable("pageId", "cv");
        cv.setVariable("navCurrent", "/cv/");
        cv.setVariable("pageTitle", "CV \u2014 " + site.person().name());
        cv.setVariable("pageDescription",
                "Curriculum vitae of " + site.person().name() + ", " + site.person().title()
                        + ". Education, projects, skills and downloadable PDF.");
        cv.setVariable("canonical", site.baseUrl() + "/cv/");
        cv.setVariable("jsonLd", jsonLd.person());
        cv.setVariable("cvProjects", cvProjects);
        write(engine, "cv", cv, out.resolve("cv/index.html"));
        pages++;

        // ---- Contact ----
        Context contact = base(site, about, fmt, md, downloads, buildId, builtAt);
        contact.setVariable("pageId", "contact");
        contact.setVariable("navCurrent", "/contact/");
        contact.setVariable("pageTitle", "Contact \u2014 " + site.person().name());
        contact.setVariable("pageDescription",
                "Get in touch with " + site.person().name() + " \u2014 email, phone and LinkedIn.");
        contact.setVariable("canonical", site.baseUrl() + "/contact/");
        contact.setVariable("jsonLd", jsonLd.person());
        write(engine, "contact", contact, out.resolve("contact/index.html"));
        pages++;

        // ---- 404 ----
        Context nf = base(site, about, fmt, md, downloads, buildId, builtAt);
        nf.setVariable("pageId", "404");
        nf.setVariable("pageTitle", "Page not found \u2014 " + site.person().name());
        nf.setVariable("pageDescription", "That page does not exist.");
        nf.setVariable("canonical", site.baseUrl() + "/404.html");
        nf.setVariable("jsonLd", jsonLd.person());
        nf.setVariable("projects", featured);
        write(engine, "404", nf, out.resolve("404.html"));
        pages++;

        // ---- Machine-readable outputs ----
        new SearchIndex().write(out.resolve("search-index.json"), projects);
        Feeds feeds = new Feeds(site);
        feeds.writeSitemap(out.resolve("sitemap.xml"), projects,
                List.of("/", "/projects/", "/about/", "/cv/", "/contact/"));
        feeds.writeRobots(out.resolve("robots.txt"));
        feeds.writeRss(out.resolve("feed.xml"), projects);
        feeds.writeNoJekyll(out.resolve(".nojekyll"));
        // Only for a real domain — see Site.usesCustomDomain(). Emitting a CNAME for a
        // domain that is not registered yet takes the site off the air at both addresses.
        if (site.usesCustomDomain()) {
            feeds.writeCname(out.resolve("CNAME"));
        }

        return new Result(pages, projects.size(), assetCount, validator.warnings());
    }

    // ------------------------------------------------------------------

    private static String ogImage(Site site, Project p) {
        if (p.seo() != null && p.seo().image() != null) return site.baseUrl() + "/" + p.seo().image();
        if (p.hero() != null) return site.baseUrl() + "/" + p.hero().src();
        if (site.person().image() != null) return site.baseUrl() + "/" + site.person().image();
        return null;
    }

    private static List<Project> featured(Site site, List<Project> projects) {
        List<Project> out = new ArrayList<>();
        List<String> wanted = site.home() == null ? List.of() : site.home().featuredList();
        if (!wanted.isEmpty()) {
            for (String slug : wanted) {
                projects.stream().filter(p -> p.slug().equals(slug)).findFirst().ifPresent(out::add);
            }
        } else {
            projects.stream().filter(Project::featured).forEach(out::add);
        }
        if (out.isEmpty()) out.addAll(projects.stream().limit(3).toList());
        return out;
    }

    private TemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private Context base(Site site, About about, Fmt fmt, Markdown md,
                         DownloadResolver dl, String buildId, String builtAt) {
        Context c = new Context(Locale.UK);
        c.setVariable("site", site);
        c.setVariable("person", site.person());
        c.setVariable("about", about);
        c.setVariable("nav", site.navList());
        c.setVariable("fmt", fmt);
        c.setVariable("md", md);
        c.setVariable("dl", dl);
        c.setVariable("buildId", buildId);
        c.setVariable("builtAt", builtAt);
        c.setVariable("year", Year.now().getValue());
        c.setVariable("needsMath", false);
        c.setVariable("hasViewer", false);
        c.setVariable("navCurrent", null);
        c.setVariable("ogImage", site.person().image() == null
                ? null : site.baseUrl() + "/" + site.person().image());
        return c;
    }

    private void write(TemplateEngine engine, String template, Context ctx, Path target) {
        String html = engine.process(template, ctx);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, html.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + target, e);
        }
    }

    private void mkdirs(Path p) {
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + p, e);
        }
    }
}

