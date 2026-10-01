package com.lore;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class LoreApplication {

    public static void main(String[] args) {
        WebApplicationType webType = WebApplicationType.NONE;
        for (String arg : args) {
            if ("ui".equals(arg)) {
                webType = WebApplicationType.SERVLET;
                break;
            }
        }

        new SpringApplicationBuilder(LoreApplication.class)
                .web(webType)
                .logStartupInfo(false)
                .run(args);
    }
}
