package com.lore;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class LoreApplication {

    public static void main(String[] args) {
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

        new SpringApplicationBuilder(LoreApplication.class)
                .web(webType)
                .logStartupInfo(false)
                .run(args);
    }
}
