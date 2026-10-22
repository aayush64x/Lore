package com.lore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;

@SpringBootApplication
public class LoreApplication {

    public static void main(String[] args) {
        // Before Spring starts, so the database isn't open yet.
        Path backup = DbBackup.run();

        WebApplicationType webType = WebApplicationType.NONE;
        boolean help = false;
        String port = null;
        for (int i = 0; i < args.length; i++) {
            if ("ui".equals(args[i])) {
                webType = WebApplicationType.SERVLET;
            } else if ("-h".equals(args[i]) || "--help".equals(args[i])) {
                help = true;
            } else if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = args[i + 1];
            } else if (args[i].startsWith("--port=")) {
                port = args[i].substring("--port=".length());
            }
        }

        if (help) {
            webType = WebApplicationType.NONE;
        }

        // The web server starts before picocli parses args, so lore ui --port
        // has to reach server.port here. System properties outrank application.yml.
        if (port != null) {
            System.setProperty("server.port", port);
        }

        ConfigurableApplicationContext context = new SpringApplicationBuilder(LoreApplication.class)
                .web(webType)
                .logStartupInfo(false)
                .run(args);

        // Spring is up, so the database opened (and migrated) fine: the copy made above is good.
        DbBackup.markGood(backup);

        // Every command but ui is done here. Close Spring now, which closes the database while
        // the JVM is still running normally, instead of in a shutdown hook as the JVM exits.
        if (webType == WebApplicationType.NONE) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
