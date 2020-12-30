package com.giorgimode.spotmystatus.controller;

import static com.giorgimode.spotmystatus.helpers.SpotConstants.SLACK_REDIRECT_PATH;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.SPOTIFY_REDIRECT_PATH;
import static org.apache.commons.lang3.StringUtils.isBlank;
import com.giorgimode.spotmystatus.slack.SlackClient;
import com.giorgimode.spotmystatus.spotify.SpotifyClient;
import java.io.IOException;
import java.util.UUID;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api")
@Slf4j
public class AuthorizationController {

    private static final String ERROR_PAGE = "/error";

    private final SpotifyClient spotifyClient;
    private final SlackClient slackClient;

    public AuthorizationController(SpotifyClient spotifyClient, SlackClient slackClient) {
        this.spotifyClient = spotifyClient;
        this.slackClient = slackClient;
    }

    @GetMapping("/start")
    public void startNewUser(HttpServletResponse httpServletResponse) {
        log.info("Starting authorization for a new user...");
        String authorization = slackClient.requestAuthorization();
        httpServletResponse.setHeader("Location", authorization);
        httpServletResponse.setStatus(302);
    }

    @RequestMapping(SLACK_REDIRECT_PATH)
    public void slackRedirect(
        @RequestParam(value = "code", required = false) String slackCode,
        @RequestParam(value = "error", required = false) String error,
        HttpServletResponse httpServletResponse) throws IOException {

        if (slackCode == null) {
            log.info("User failed to grant permission on Slack. Received error {}", error);
            httpServletResponse.sendRedirect(ERROR_PAGE);
            return;
        }

        log.info("User has granted permission on Slack. Received code {}", slackCode);
        UUID state = slackClient.updateAuthToken(slackCode);

        log.info("Slack authorization successful using code {} and state {}. Requesting authorization on spotify", slackCode, state);
        String authorization = spotifyClient.requestAuthorization(state);
        httpServletResponse.setHeader("Location", authorization);
        httpServletResponse.setStatus(302);
    }

    @RequestMapping(SPOTIFY_REDIRECT_PATH)
    public void spotifyRedirect(@RequestParam(value = "code", required = false) String spotifyCode,
        @RequestParam(value = "state", required = false) UUID state,
        @RequestParam(value = "error", required = false) String error,
        HttpServletResponse httpServletResponse) throws IOException {

        if (state == null || isBlank(spotifyCode)) {
            log.info("User failed to grant permission on Spotify. Received code {}, state {} with error {}", spotifyCode, state, error);
            httpServletResponse.sendRedirect(ERROR_PAGE);
            return;
        }

        log.info("User has granted permission on Spotify. Received code {} for state {}", spotifyCode, state);
        spotifyClient.updateAuthToken(spotifyCode, state);
        httpServletResponse.sendRedirect("/success");
    }
}
