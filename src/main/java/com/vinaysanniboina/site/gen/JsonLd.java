package com.vinaysanniboina.site.gen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinaysanniboina.site.model.About;
import com.vinaysanniboina.site.model.Project;
import com.vinaysanniboina.site.model.Site;
import com.vinaysanniboina.site.render.Markdown;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * schema.org structured data, emitted as a {@code <script type="application/ld+json">} block.
 *
 * <p>Why bother: Google reads this to build the knowledge panel for a person, and recruiters'
 * tooling increasingly parses it. {@code Person} goes on every page; {@code CreativeWork} goes on
 * each project page and describes the project as a piece of engineering work rather than a blog post.
 */
public final class JsonLd {

    private final ObjectMapper json = new ObjectMapper();
    private final Markdown md = new Markdown();
    private final Site site;
    private final About about;

    public JsonLd(Site site, About about) {
        this.site = site;
        this.about = about;
    }

    public String person() {
        return write(personNode());
    }

    private Map<String, Object> personNode() {
        Site.Person p = site.person();
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("@context", "https://schema.org");
        n.put("@type", "Person");
        n.put("@id", site.baseUrl() + "/#person");
        n.put("name", p.name());
        n.put("jobTitle", p.title());
        // Deliberately NOT emitting "email". schema.org allows it, but putting the
        // address in the page source in plain text would undo the obfuscation used
        // everywhere else — and search engines do not surface it anyway. Contact
        // routes through the site and LinkedIn instead.
        n.put("url", site.baseUrl() + "/");
        if (p.image() != null) n.put("image", site.baseUrl() + "/" + p.image());
        n.put("description", md.text(about.profile()).substring(0, Math.min(300, md.text(about.profile()).length())));

        Map<String, Object> address = new LinkedHashMap<>();
        address.put("@type", "PostalAddress");
        address.put("addressLocality", p.location());
        n.put("address", address);

        List<String> sameAs = new ArrayList<>(p.sameAsList());
        if (p.linkedin() != null && !sameAs.contains(p.linkedin())) sameAs.add(p.linkedin());
        if (p.github() != null && !sameAs.contains(p.github())) sameAs.add(p.github());
        if (!sameAs.isEmpty()) n.put("sameAs", sameAs);

        List<Map<String, Object>> alumni = new ArrayList<>();
        for (About.Education e : about.educationList()) {
            Map<String, Object> org = new LinkedHashMap<>();
            org.put("@type", "EducationalOrganization");
            org.put("name", e.institution());
            alumni.add(org);
        }
        if (!alumni.isEmpty()) n.put("alumniOf", alumni);

        List<String> knows = new ArrayList<>();
        about.skillList().forEach(g -> g.itemList().forEach(s -> knows.add(s.name())));
        if (!knows.isEmpty()) n.put("knowsAbout", knows);

        List<Map<String, Object>> memberships = new ArrayList<>();
        for (About.Membership m : about.membershipList()) {
            Map<String, Object> org = new LinkedHashMap<>();
            org.put("@type", "Organization");
            org.put("name", m.body());
            memberships.add(org);
        }
        if (!memberships.isEmpty()) n.put("memberOf", memberships);

        return n;
    }

    /** A graph with the Person plus a CreativeWork for the project itself. */
    public String project(Project p) {
        Map<String, Object> work = new LinkedHashMap<>();
        work.put("@type", "CreativeWork");
        work.put("@id", site.baseUrl() + p.url() + "#project");
        work.put("name", p.title());
        work.put("headline", p.title());
        work.put("url", site.baseUrl() + p.url());
        work.put("description", p.summary());
        work.put("author", Map.of("@id", site.baseUrl() + "/#person"));
        work.put("creator", Map.of("@id", site.baseUrl() + "/#person"));
        if (p.date() != null) work.put("dateCreated", p.date().toString());
        if (p.hero() != null) work.put("image", site.baseUrl() + "/" + p.hero().src());
        if (!p.tagList().isEmpty()) work.put("keywords", String.join(", ", p.tagList()));
        if (p.organisation() != null) {
            work.put("sourceOrganization", Map.of("@type", "Organization", "name", p.organisation()));
        }
        if (!p.toolList().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (String t : p.toolList()) {
                tools.add(Map.of("@type", "SoftwareApplication", "name", t,
                        "applicationCategory", "EngineeringApplication"));
            }
            work.put("instrument", tools);
        }
        work.put("inLanguage", site.lang() == null ? "en-GB" : site.lang());
        work.put("isPartOf", Map.of("@type", "WebSite", "@id", site.baseUrl() + "/#website"));

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("@context", "https://schema.org");
        graph.put("@graph", List.of(stripContext(personNode()), work));
        return write(graph);
    }

    public String website() {
        Map<String, Object> web = new LinkedHashMap<>();
        web.put("@type", "WebSite");
        web.put("@id", site.baseUrl() + "/#website");
        web.put("url", site.baseUrl() + "/");
        web.put("name", site.title());
        web.put("description", site.description());
        web.put("publisher", Map.of("@id", site.baseUrl() + "/#person"));
        web.put("inLanguage", site.lang() == null ? "en-GB" : site.lang());

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("@context", "https://schema.org");
        graph.put("@graph", List.of(stripContext(personNode()), web));
        return write(graph);
    }

    private static Map<String, Object> stripContext(Map<String, Object> node) {
        Map<String, Object> copy = new LinkedHashMap<>(node);
        copy.remove("@context");
        return copy;
    }

    private String write(Object node) {
        try {
            // Escape the closing script sequence so the JSON can never break out of the <script> block.
            return json.writerWithDefaultPrettyPrinter().writeValueAsString(node).replace("</", "<\\/");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not build JSON-LD: " + e.getMessage(), e);
        }
    }
}
