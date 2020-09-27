package com.giorgimode.SpotMyStatus.slack;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class SlackController {

    @Autowired
    public SlackAgent slackAgent;

    @RequestMapping("/redirect2")
    public String redirectEndpoint(@RequestParam(value = "code") String slackCode) {
        log.info("****Slack Code: x" + slackCode);
        slackAgent.updateAuthToken(slackCode);
        return slackAgent.updateStatus();
    }
}