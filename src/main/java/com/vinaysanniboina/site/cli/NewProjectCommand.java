package com.vinaysanniboina.site.cli;

import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * Scaffolds a new project: one content file plus the two folders its images and binaries go in.
 *
 * <pre>
 *   java -jar target/site-builder.jar new-project rear-upright-fea --title "Rear Upright FEA"
 * </pre>
 */
@CommandLine.Command(
        name = "new-project",
        description = "Create a stub content file and the matching image and file folders.")
public final class NewProjectCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "URL slug, e.g. rear-upright-fea")
    String slug;

    @CommandLine.Option(names = "--title", description = "Project title. Defaults to a title-cased slug.")
    String title;

    @CommandLine.Option(names = {"-r", "--root"}, description = "Repository root (default: ${DEFAULT-VALUE}).")
    Path root = Path.of(".");

    @CommandLine.Option(names = "--force", description = "Overwrite an existing content file.")
    boolean force;

    @Override
    public Integer call() throws IOException {
        String s = slug.trim().toLowerCase(Locale.UK);
        if (!s.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException(
                    "Slug '" + slug + "' must be lowercase letters, digits and single hyphens — "
                            + "it becomes the page URL, e.g. rear-upright-fea");
        }
        Path file = root.resolve("content/projects/" + s + ".yaml");
        if (Files.exists(file) && !force) {
            throw new IllegalStateException(file + " already exists. Pass --force to overwrite it.");
        }

        String name = title != null && !title.isBlank() ? title : titleCase(s);
        Path imageDir = root.resolve("assets/img/projects/" + s);
        Path fileDir = root.resolve("files/" + s);
        Files.createDirectories(file.getParent());
        Files.createDirectories(imageDir);
        Files.createDirectories(fileDir);

        // .gitkeep so the empty folders survive a git commit
        writeIfAbsent(imageDir.resolve(".gitkeep"), "");
        writeIfAbsent(fileDir.resolve(".gitkeep"), "");

        Files.writeString(file, template(s, name));

        System.out.println("Created:");
        System.out.println("  " + rel(file) + "        <- write the project here");
        System.out.println("  " + rel(imageDir) + "/   <- put screenshots, contour plots and photos here");
        System.out.println("  " + rel(fileDir) + "/    <- put CAD and simulation files here (never committed to git)");
        System.out.println();
        System.out.println("Next:");
        System.out.println("  1. Fill in the content file. Delete any section you have nothing to say about.");
        System.out.println("  2. java -jar target/site-builder.jar upload      (if you added files)");
        System.out.println("  3. java -jar target/site-builder.jar serve       (check it locally)");
        System.out.println("  4. git add -A && git commit -m \"Add " + name + "\" && git push");
        return 0;
    }

    private void writeIfAbsent(Path p, String body) throws IOException {
        if (!Files.exists(p)) Files.writeString(p, body);
    }

    private String rel(Path p) {
        return root.toAbsolutePath().relativize(p.toAbsolutePath()).toString().replace('\\', '/');
    }

    private static String titleCase(String slug) {
        StringBuilder sb = new StringBuilder();
        for (String part : slug.split("-")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private static String template(String slug, String title) {
        return """
                # ---------------------------------------------------------------------------
                # %2$s
                #
                # Every section below is OPTIONAL except title and summary. Delete whole blocks
                # you have nothing to say about — the page renders only what is present.
                # Full field reference: ADDING_A_PROJECT.md
                # ---------------------------------------------------------------------------

                title: "%2$s"
                subtitle: ""                 # one clause under the title, e.g. "ANSYS Fluent, 2026"
                summary: >
                  Two or three sentences. What the problem was, what you did, what the outcome
                  was. This is what shows on the project card and in search results.

                date: %3$s                   # used for sorting and the sitemap
                status: "In progress"        # free text: Complete / In progress / Ongoing to Jun 2026
                featured: false              # true puts it on the home page
                order: 100                   # lower sorts first on the projects index

                tags: []                     # filter buttons: CAD, CFD, FEA, EV Systems, Formula Student
                tools: []                    # SOLIDWORKS, ANSYS Fluent, SpaceClaim, MATLAB, Excel
                role: ""                     # what YOU did, e.g. "Chassis design and validation"
                organisation: ""             # team, university or module
                timeline: ""                 # e.g. "Sep 2025 - Jun 2026"

                # hero:
                #   src: assets/img/projects/%1$s/hero.jpg
                #   alt: "Describe what an engineer should see in this image."
                #   caption: ""

                # Up to four headline numbers shown under the title. Only real, defensible numbers.
                # metrics:
                #   - value: "45%%"
                #     label: "drag reduction"
                #     note: "vs. baseline geometry"

                # 1 -- Overview and objective -----------------------------------------------
                objective: ""                # one sentence: what this work had to achieve
                overview: |
                  Markdown. A few paragraphs setting up the problem and why it mattered.

                # 2 -- Design brief, requirements and constraints ---------------------------
                # brief:
                #   context: |
                #     Markdown. Where the requirement came from.
                #   requirements:
                #     - id: R1
                #       text: "Torsional stiffness at or above 1000 Nm/deg."
                #       rationale: "So suspension geometry behaves as designed."
                #       verification: "Twist test rig, dial gauges at four corners."
                #   constraints:
                #     - "Fixed competition deadline."
                #   out_of_scope:
                #     - "Full vehicle CFD."

                # 3 -- Design decisions -----------------------------------------------------
                # This is the section reviewers read most closely. One entry per real decision.
                # decisions:
                #   - title: "Short name for the decision"
                #     decision: "What you chose, stated plainly."
                #     alternatives:
                #       - option: "The other option you looked at"
                #         why_not: "Why you rejected it. Be specific and honest."
                #     rationale: |
                #       Markdown. Why the chosen option won, with numbers where you have them.
                #     consequences: "What this cost you elsewhere."
                #     evidence: "Test, calculation or simulation that backs it up."

                # 4 -- Engineering logic and methodology ------------------------------------
                # methodology: |
                #   Markdown. How you actually worked the problem, step by step.

                # 5 -- Calculations ---------------------------------------------------------
                # Maths is TeX WITHOUT dollar signs. Rendered with KaTeX.
                # calculations:
                #   - title: "Aerodynamic downforce at 60 km/h"
                #     intro: "Why this calculation matters."
                #     assumptions:
                #       - "Sea-level air, 15 C."
                #     symbols:
                #       - symbol: "\\\\rho"
                #         meaning: "Air density"
                #         value: "1.225"
                #         unit: "kg/m^3"
                #         source: "ISA sea level"
                #     display_math: "F_L = \\\\tfrac{1}{2} \\\\rho V^2 A C_L"
                #     steps:
                #       - description: "Substitute the values"
                #         math: "F_L = 0.5 \\\\times 1.225 \\\\times 16.7^2 \\\\times 0.9 \\\\times 1.4"
                #         result: "215 N"
                #     result: "Roughly 215 N of downforce at 60 km/h."
                #     note: "Order of magnitude only; validate against the CFD result."

                # 6 -- Simulation setup and results -----------------------------------------
                # simulation:
                #   software: "ANSYS Fluent 2023 R2"
                #   solver: "Pressure-based, steady, SIMPLE"
                #   domain: "Half-domain with symmetry plane."
                #   turbulence_model: "k-omega SST"
                #   mesh:
                #     type: "Poly-hexcore"
                #     elements: "8.4 million"
                #     y_plus: "< 1"
                #     inflation_layers: "15, growth 1.2"
                #     independence: |
                #       Markdown. How you established the answer stopped moving.
                #     independence_table:
                #       caption: "Mesh independence study"
                #       columns: ["Mesh", "Elements", "C_d", "Change"]
                #       rows:
                #         - ["Coarse", "2.1 M", "0.412", "-"]
                #   boundary_conditions:
                #     - name: "Inlet"
                #       value: "16.7 m/s velocity inlet"
                #   convergence: "Residuals below 1e-5; drag monitor flat over 400 iterations."
                #   results: |
                #     Markdown. What the simulation showed.
                #   figures:
                #     - src: assets/img/projects/%1$s/pressure-contour.png
                #       alt: "Static pressure contour on the wing suction surface."
                #       caption: "Static pressure, mid-span."

                # 7 -- Validation, testing and what went wrong -------------------------------
                # validation:
                #   approach: |
                #     Markdown. How you checked the result was real.
                #   results: |
                #     Markdown.
                #   issues:
                #     - "Something that went wrong, and what it cost."
                #   lessons:
                #     - "What you would do differently."
                #   future_work: "What you would do with more time."

                # 8 -- Gallery ---------------------------------------------------------------
                # gallery:
                #   - src: assets/img/projects/%1$s/photo-1.jpg
                #     alt: "Required. Describe the image."
                #     caption: "Shown under the image in the lightbox."

                # 9 -- Downloads -------------------------------------------------------------
                # 'file' is a path under files/. Run `upload` and size, checksum and URL fill in
                # automatically. Always include at least one neutral format.
                # downloads:
                #   - label: "Full assembly (STEP)"
                #     file: files/%1$s/assembly.step
                #     requires: "Any CAD package, or a free STEP viewer"
                #     neutral_format: true
                #   - label: "SOLIDWORKS assembly"
                #     file: files/%1$s/assembly.SLDASM
                #     requires: "SOLIDWORKS 2023 or newer"
                #     note: "References the parts in the same archive."

                # 10 -- Optional 3D viewer ----------------------------------------------------
                # viewer:
                #   model: files/%1$s/preview.stl
                #   poster: assets/img/projects/%1$s/render.jpg
                #   poster_alt: "Rendered view of the assembly."
                #   note: "Simplified mesh, roughly 4 MB."

                # links:
                #   - label: "Competition regulations"
                #     url: "https://www.imeche.org/events/formula-student"
                """.formatted(slug, title, LocalDate.now());
    }
}
