package com.homefix.api.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomePageController {

    private final String frontendUrl;

    public HomePageController(
            @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("frontendUrl", frontendUrl);
        return "home";
    }
}
