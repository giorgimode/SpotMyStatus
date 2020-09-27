package com.giorgimode.SpotMyStatus.spotify;

import com.giorgimode.SpotMyStatus.slack.SlackAgent;
import java.net.URI;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class SpotifyController {

    @Autowired
    public SpotifyAgent spotifyAgent;

    @Autowired
    public SlackAgent slackAgent;

    @RequestMapping("/start")
    public void triggerApiCall(HttpServletResponse httpServletResponse) {
        String authorization = slackAgent.requestAuthorization();
        log.info("****Redirect1***" + authorization);
        httpServletResponse.setHeader("Location", authorization);
        httpServletResponse.setStatus(302);
    }

    @RequestMapping("/redirect2")
    public void redirectEndpoint2(@RequestParam(value = "code") String slackCode, HttpServletResponse httpServletResponse) {
        log.info("****Slack Code: x" + slackCode);
        slackAgent.updateAuthToken(slackCode);

        URI authorization = spotifyAgent.requestAuthorization();
        log.info("****Redirect2***" + authorization.toString());
        httpServletResponse.setHeader("Location", authorization.toString());
        httpServletResponse.setStatus(302);
    }

    @RequestMapping("/redirect")
    public void redirectEndpoint(@RequestParam(value = "code") String spotifyCode, @RequestParam(value = "state") String state) {
        log.info("Code {}, state {}", spotifyCode, state);
        spotifyAgent.updateAuthToken(spotifyCode);
        slackAgent.updateStatus();
    }


}