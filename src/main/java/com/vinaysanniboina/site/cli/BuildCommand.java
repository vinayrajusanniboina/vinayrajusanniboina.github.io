package com.vinaysanniboina.site.cli;

import com.vinaysanniboina.site.build.SiteBuilder;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "build",
        description = "Validate the content and render the static site into the output directory.")
public final class BuildCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-r", "--root"}, description = "Repository root (default: ${DEFAULT-VALUE}).")
    Path root = Path.of(".");

    @CommandLine.Option(names = {"-o", "--out"}, description = "Output directory (default: ${DEFAULT-VALUE}).")
    Path out = Path.of("docs");

    @CommandLine.Option(names = "--strict", description = "Treat warnings as errors. Used by CI.")
    boolean strict;

    @Override
    public Integer call() {
        long t0 = System.currentTimeMillis();
        SiteBuilder.Result r = new SiteBuilder(root, root.resolve(out), strict).build();

        if (!r.warnings().isEmpty()) {
            System.out.println();
            System.out.println(r.warnings().size() + " warning(s):");
            r.warnings().forEach(w -> System.out.println("  warn  " + w));
        }
        System.out.println();
        System.out.printf("Built %d pages (%d projects) and copied %d assets to %s in %d ms.%n",
                r.pages(), r.projects(), r.assets(),
                root.resolve(out).toAbsolutePath().normalize(),
                System.currentTimeMillis() - t0);
        System.out.println("Preview it with:  java -jar target/site-builder.jar serve");
        return 0;
    }
}
