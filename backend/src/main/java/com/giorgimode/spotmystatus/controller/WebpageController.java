package com.giorgimode.spotmystatus.controller;

import com.giorgimode.spotmystatus.model.SubmissionForm;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api")
@Slf4j
public class WebpageController {


    @PostMapping("/support")
    public void handleSupport(@ModelAttribute SubmissionForm supportSubmission, HttpServletResponse httpServletResponse) throws IOException {
        log.info("Received {}", supportSubmission);
        httpServletResponse.sendRedirect("/submitted.html");
    }
}
