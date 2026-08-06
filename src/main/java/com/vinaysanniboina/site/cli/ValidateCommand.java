package com.vinaysanniboina.site.cli;

import com.vinaysanniboina.site.content.ContentLoader;
import com.vinaysanniboina.site.content.Validator;
import com.vinaysanniboina.site.model.About;
import com.vinaysanniboina.site.model.Project;
import com.vinaysanniboina.site.model.Site;
import com.vinaysanniboina.site.model.UploadLock;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "validate",
        description = "Check every content file against the schema without rendering anything.")
public final class ValidateCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-r", "--root"}, description = "Repository root (default: ${DEFAULT-VALUE}).")
    Path root = Path.of(".");

    @CommandLine.Option(names = "--strict", description = "Exit non-zero if there are warnings.")
    boolean strict;

    @Override
    public Integer call() {
        ContentLoader loader = new ContentLoader(root);
        Site site = loader.loadSite();
        About about = loader.loadAbout();
        List<Project> projects = loader.loadProjects();
        UploadLock lock = loader.loadUploadLock();

        Validator v = new Validator(root);
        v.validate(site, about, projects, lock);

        v.errors().forEach(e -> System.out.println("ERROR  " + e));
        v.warnings().forEach(w -> System.out.println("warn   " + w));

        System.out.println();
        System.out.printf("%d project(s) checked. %d error(s), %d warning(s).%n",
                projects.size(), v.errors().size(), v.warnings().size());

        if (!v.ok()) return 1;
        if (strict && !v.warnings().isEmpty()) return 1;
        System.out.println("Content is valid.");
        return 0;
    }
}
