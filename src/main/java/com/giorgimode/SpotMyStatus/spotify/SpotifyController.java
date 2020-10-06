package com.giorgimode.SpotMyStatus.spotify;

import static com.giorgimode.SpotMyStatus.common.SpotConstants.SLACK_REDIRECT_PATH;
import com.giorgimode.SpotMyStatus.slack.SlackClient;
import java.util.UUID;
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
    public SpotifyClient spotifyClient;

    @Autowired
    public SlackClient slackClient;

    @RequestMapping("/start")
    public void startNewUser(HttpServletResponse httpServletResponse) {
        log.info("Starting authorization for a new user...");
        String authorization = slackClient.requestAuthorization();
        httpServletResponse.setHeader("Location", authorization);
        httpServletResponse.setStatus(302);
    }

    @RequestMapping(SLACK_REDIRECT_PATH)
    public void slackRedirect(@RequestParam(value = "code") String slackCode, HttpServletResponse httpServletResponse) {
        log.info("User has granted permission on Slack. Received code {}", slackCode);
        UUID state = slackClient.updateAuthToken(slackCode);

        log.info("Slack authorization successful using code {} and state {}. Requesting authorization on spotify", slackCode, state);
        String authorization = spotifyClient.requestAuthorization(state);
        httpServletResponse.setHeader("Location", authorization);
        httpServletResponse.setStatus(302);
    }

    @RequestMapping("/spotify/redirect")
    public void spotifyRedirect(@RequestParam(value = "code") String spotifyCode, @RequestParam(value = "state") UUID state) {
        log.info("User has granted permission on Spotify. Received code {} for state {}", spotifyCode, state);
        spotifyClient.updateAuthToken(spotifyCode, state);
        //todo add a welcome page here
    }
}