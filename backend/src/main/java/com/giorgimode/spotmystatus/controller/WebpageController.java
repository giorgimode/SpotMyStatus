package com.giorgimode.spotmystatus.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Slf4j
public class WebpageController {

    @GetMapping("/")
    public String redirectToHome() {
        return "redirect:https://spotmystatus.com";
    }
}
