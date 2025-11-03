package com.giorgimode.spotmystatus.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api")
@Slf4j
public class UserInteractionController {


    @PostMapping(value = "/slack/command", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String receiveSlackCommand(
            @RequestBody String body) {

        log.trace("Received a slack command {}", body);
        return "Hey there! SpotMyStatus has a new home. Sign up at <https://spotmystatus.com/|spotmystatus.com>";
    }
}
