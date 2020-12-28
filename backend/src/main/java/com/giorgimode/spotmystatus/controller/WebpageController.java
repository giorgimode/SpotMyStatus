package com.giorgimode.spotmystatus.controller;

import com.giorgimode.spotmystatus.model.SubmissionForm;
import com.giorgimode.spotmystatus.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@Slf4j
public class WebpageController {

    private final MailService mailService;

    public WebpageController(MailService mailService) {
        this.mailService = mailService;
    }

    @PostMapping("/support")
    public String handleSupport(@ModelAttribute SubmissionForm supportSubmission, ModelMap model) {
        log.info("Received support message regarding {}", supportSubmission.getSubject());
        try {
            mailService.sendEmail(supportSubmission);
            model.addAttribute("formResult", "success");
        } catch (Exception e) {
            model.addAttribute("formResult", "fail");
        }
        return "support";
    }

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
