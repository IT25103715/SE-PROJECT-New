package com.cinemahub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** Renders the friendly 403 page that SecurityConfig forwards to on an access-denied. */
@Controller
public class ErrorPageController {

    // Any HTTP method: Spring Security forwards a denied request here with its ORIGINAL method, so a
    // denied POST (or a failed CSRF check) used to hit a GET-only mapping and surface as a 500.
    @RequestMapping("/error/403")
    public String accessDenied() {
        return "error/403";
    }
}
