package com.giorgimode.SpotMyStatus.slack;

import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlackController {

    @Autowired
    public SlackAgent slackAgent;

    @RequestMapping("/test2")
    public String currentTrack() {
        return slackAgent.updateStatus();
    }


    @RequestMapping("/start2")
    public void triggerApiCall(HttpServletResponse httpServletResponse) {
        String authorization = slackAgent.requestAuthorization();
        System.out.println("****Redirect***" + authorization);
        httpServletResponse.setHeader("Location", authorization);
        httpServletResponse.setStatus(302);
    }

    @RequestMapping("/redirect2")
    public String redirectEndpoint(@RequestParam(value = "code") String slackCode) {
        System.out.println("****Slack Code: x" + slackCode);
        slackAgent.updateAuthToken(slackCode);
        return "Say hello to my little friend!";
    }
}