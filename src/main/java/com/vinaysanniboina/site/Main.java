package com.vinaysanniboina.site;

import com.vinaysanniboina.site.cli.BuildCommand;
import com.vinaysanniboina.site.cli.NewProjectCommand;
import com.vinaysanniboina.site.cli.ServeCommand;
import com.vinaysanniboina.site.cli.UploadCommand;
import com.vinaysanniboina.site.cli.ValidateCommand;
import picocli.CommandLine;

/**
 * Entry point.
 *
 * <pre>
 *   java -jar target/site-builder.jar build              # validate + render into docs/
 *   java -jar target/site-builder.jar validate           # check content, render nothing
 *   java -jar target/site-builder.jar serve              # build, then serve on http://localhost:8080
 *   java -jar target/site-builder.jar new-project my-slug # scaffold a project
 *   java -jar target/site-builder.jar upload             # push new/changed binaries to Cloudflare R2
 * </pre>
 */
@CommandLine.Command(
        name = "site",
        mixinStandardHelpOptions = true,
        version = "vinayrajusanniboina.com site builder 1.0.0",
        description = "Static site generator and asset pipeline for vinayrajusanniboina.com.",
        subcommands = {
                BuildCommand.class,
                ValidateCommand.class,
                ServeCommand.class,
                NewProjectCommand.class,
                UploadCommand.class,
                CommandLine.HelpCommand.class
        })
public final class Main implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    public static void main(String[] args) {
        // No arguments at all is almost always "build" — make the common case free.
        String[] effective = args.length == 0 ? new String[]{"build"} : args;
        int exit = new CommandLine(new Main())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                    cmd.getErr().println();
                    cmd.getErr().println(cmd.getColorScheme().errorText(ex.getMessage() == null
                            ? ex.toString() : ex.getMessage()));
                    if (System.getenv("SITE_DEBUG") != null) ex.printStackTrace(cmd.getErr());
                    return 1;
                })
                .execute(effective);
        System.exit(exit);
    }
}
