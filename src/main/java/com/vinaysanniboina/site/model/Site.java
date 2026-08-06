package com.vinaysanniboina.site.model;

import java.util.List;

/**
 * Everything in {@code content/site.yaml}: identity, contact details, navigation and the home page.
 *
 * <p>Phone number, email and domain live here and nowhere else, so changing them is a one-file edit.
 */
public record Site(
        String url,
        String title,
        String description,
        String lang,
        String locale,
        String accent,
        Person person,
        List<NavItem> nav,
        Home home,
        Contact contact,
        Cv cv,
        Analytics analytics,
        Downloads downloads
) {
    public List<NavItem> navList() { return nav == null ? List.of() : nav; }

    /** {@code https://www.vinayrajusanniboina.com} with any trailing slash removed. */
    public String baseUrl() {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** The bare host, used to write the CNAME file. */
    public String host() {
        return baseUrl().replaceFirst("^https?://", "");
    }

    /**
     * True when the site is served from a real domain rather than a github.io address.
     *
     * <p>This gates the CNAME file. GitHub Pages reads CNAME as "route this site at that
     * hostname" — so shipping one for a domain that is not registered yet takes the site
     * off the air at both addresses, because the github.io URL then redirects to a host
     * that does not resolve. Publishing to {@code <user>.github.io} must therefore emit no
     * CNAME at all. Buy the domain, change {@code url:} in site.yaml, and it reappears.
     */
    public boolean usesCustomDomain() {
        String h = host();
        return !h.isEmpty() && !h.endsWith(".github.io") && !h.equals("github.io");
    }

    public record Person(
            String name,
            String title,
            String email,
            String phone,
            String location,
            String linkedin,
            String github,
            String image,
            String imageAlt,
            /** short one-line positioning statement used under the name on the home page */
            String positioning,
            List<String> sameAs
    ) {
        public List<String> sameAsList() { return sameAs == null ? List.of() : sameAs; }

        /** "vinayraj.3611" of "vinayraj.3611@gmail.com" — used by the scraper-resistant email link. */
        public String emailUser() { return email == null ? "" : email.substring(0, email.indexOf('@')); }

        /** "gmail.com" of "vinayraj.3611@gmail.com" */
        public String emailDomain() { return email == null ? "" : email.substring(email.indexOf('@') + 1); }

        /** tel: href with spaces stripped */
        public String phoneHref() { return phone == null ? "" : phone.replaceAll("[^+0-9]", ""); }
    }

    public record NavItem(String label, String href) {}

    public record Home(
            String headline,
            String intro,
            List<Project.Metric> facts,
            List<Cta> ctas,
            /** slugs, in the order they should appear; empty means "all projects marked featured" */
            List<String> featured,
            String currently
    ) {
        public List<Project.Metric> factList() { return facts == null ? List.of() : facts; }
        public List<Cta> ctaList() { return ctas == null ? List.of() : ctas; }
        public List<String> featuredList() { return featured == null ? List.of() : featured; }

        public record Cta(String label, String href, boolean primary) {}
    }

    public record Contact(
            String intro,
            /** null or blank -> the page uses a plain mailto link instead of a form */
            String formEndpoint,
            String formNote,
            String availability
    ) {}

    public record Cv(String pdf, String updated, String note) {}

    /** Optional privacy-friendly analytics snippet; leave null for none. */
    public record Analytics(String scriptUrl, String siteId) {}

    /** Where uploaded binaries are served from, e.g. https://files.vinayrajusanniboina.com */
    public record Downloads(String publicBaseUrl, String note) {
        public String base() {
            if (publicBaseUrl == null) return "";
            return publicBaseUrl.endsWith("/")
                    ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                    : publicBaseUrl;
        }
    }
}
