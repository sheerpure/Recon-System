package com.fintech.recon_system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * WebController handles Page Navigation (Thymeleaf Rendering).
 * Unlike RestControllers, these methods return the name of the HTML file.
 */
@Controller
public class WebController {

    /**
     * Serves the Login Page.
     * Accessible via http://localhost:8081/login
     */
    @GetMapping("/login")
    public String login() {
        return "login"; // Looks for src/main/resources/templates/login.html
    }

}