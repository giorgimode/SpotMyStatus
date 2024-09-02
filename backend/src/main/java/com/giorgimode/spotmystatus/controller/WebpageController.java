package com.giorgimode.spotmystatus.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@Slf4j
public class WebpageController {

    @GetMapping("/support")
    public String getSupportPage() {
        return "support";
    }

    @GetMapping("/success")
    public String getSuccessPage() {
        return "success";
    }

    @GetMapping("/privacy")
    public String getPrivacyPage() {
        return "privacy";
    }

    @GetMapping("/terms")
    public String getTermsAndConditionsPage() {
        return "terms";
    }

    @RequestMapping("/error")
    public String handleError() {
        return "error";
    }
}
