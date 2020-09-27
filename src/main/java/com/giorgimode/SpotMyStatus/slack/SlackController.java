package com.giorgimode.SpotMyStatus.slack;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlackController {

    @Autowired
    public SlackAgent slackAgent;

    @RequestMapping("/redirect2")
    public String redirectEndpoint(@RequestParam(value = "code") String slackCode) {
        System.out.println("****Slack Code: x" + slackCode);
        slackAgent.updateAuthToken(slackCode);
        return slackAgent.updateStatus();
    }
}