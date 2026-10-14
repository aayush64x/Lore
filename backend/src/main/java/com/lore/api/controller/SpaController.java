package com.lore.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React app for its client-side routes, so refreshing or opening
 * /sessions/{id} directly loads index.html instead of returning a 404.
 */
@Controller
public class SpaController {

    @GetMapping("/sessions/{id}")
    public String session() {
        return "forward:/index.html";
    }
}
