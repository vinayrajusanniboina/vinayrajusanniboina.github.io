package com.vinaysanniboina.site.render;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.List;

/**
 * Markdown for the prose fields in content files (overview, rationale, results, ...).
 *
 * <p>Exposed to templates as {@code ${md.render(...)}}. Output is inserted with {@code th:utext},
 * so this is the one place where HTML is not escaped — which is fine, because the input is
 * Vinay's own content files, not user input.
 */
public final class Markdown {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public Markdown() {
        List<org.commonmark.Extension> extensions = List.of(TablesExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    /** Block-level render: wraps in &lt;p&gt;, supports lists, tables, links, code. */
    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }

    /** Inline render: same, but strips the wrapping paragraph so it can sit inside a &lt;td&gt; or &lt;li&gt;. */
    public String inline(String markdown) {
        String html = render(markdown).trim();
        if (html.startsWith("<p>") && html.endsWith("</p>")
                && html.indexOf("<p>", 3) < 0) {
            return html.substring(3, html.length() - 4);
        }
        return html;
    }

    /** Plain text, for meta descriptions and the search index. */
    public String text(String markdown) {
        if (markdown == null) return "";
        return render(markdown)
                .replaceAll("<[^>]+>", " ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public boolean isSet(String s) {
        return s != null && !s.isBlank();
    }
}
