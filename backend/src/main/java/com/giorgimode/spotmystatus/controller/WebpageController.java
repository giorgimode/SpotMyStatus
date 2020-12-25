package com.giorgimode.spotmystatus.controller;

import com.giorgimode.spotmystatus.model.SubmissionForm;
import com.giorgimode.spotmystatus.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

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
        return "support.html";
    }

    @GetMapping("/support")
    public String getSupportPage(@ModelAttribute SubmissionForm supportSubmission) {
        return "support.html";
    }
}
