package com.giorgimode.SpotMyStatus;

import static com.giorgimode.SpotMyStatus.common.SpotConstants.SLACK_REDIRECT_PATH;
import static com.giorgimode.SpotMyStatus.common.SpotConstants.SPOTIFY_REDIRECT_PATH;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import com.giorgimode.SpotMyStatus.slack.SlackClient;
import com.giorgimode.SpotMyStatus.spotify.SpotifyClient;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Slf4j
public class SpotMyStatusController {

    private static final List<String> PAUSE_COMMANDS = List.of("pause", "stop");
    private static final List<String> RESUME_COMMANDS = List.of("unpause", "play", "resume");
    private static final List<String> REMOVE_COMMANDS = List.of("remove", "purge");

    @Autowired
    private SpotifyClient spotifyClient;

    @Autowired
    private SlackClient slackClient;

    @RequestMapping("/start")
    public void startNewUser(HttpServletResponse httpServletResponse) {
        log.info("Starting authorization for a new user...");
        String authorization = slackClient.requestAuthorization();
        httpServletResponse.setHeader("Location", authorization);
        httpServletResponse.setStatus(302);
    }

    @RequestMapping(SLACK_REDIRECT_PATH)
    public void slackRedirect(@RequestParam(value = "code", required = false) String slackCode,
        @RequestParam(value = "error", required = false) String error,
        HttpServletResponse httpServletResponse) throws IOException {

        if (slackCode == null) {
            log.info("User failed to grant permission on Slack. Received code {} with error {}", slackCode, error);
            httpServletResponse.sendRedirect("/error.html");
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
            httpServletResponse.sendRedirect("/error.html");
            return;
        }

        log.info("User has granted permission on Spotify. Received code {} for state {}", spotifyCode, state);
        spotifyClient.updateAuthToken(spotifyCode, state);
        httpServletResponse.sendRedirect("/success.html");
    }

    @PostMapping(value = "/slack/command", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String receiveSlackCommand(
        @RequestHeader("X-Slack-Request-Timestamp") Long timestamp,
        @RequestHeader("X-Slack-Signature") String signature,
        @RequestParam Map<String, String> fields,
        @RequestBody String bodyString) {

        log.trace("Received a slack command {}", bodyString);
        slackClient.validateSignature(timestamp, signature, bodyString);
        String command = trimToEmpty(fields.get("text")).toLowerCase();
        String userId = trimToEmpty(fields.get("user_id"));
        if (PAUSE_COMMANDS.contains(command)) {
            log.debug("Pausing updates for user {}", userId);
            return slackClient.pause(userId);
        }
        if (RESUME_COMMANDS.contains(command)) {
            log.debug("Resuming updates for user {}", userId);
            return slackClient.resume(userId);
        }
        if (REMOVE_COMMANDS.contains(command)) {
            log.debug("Removing all data for user {}", userId);
            return slackClient.purge(userId);
        }

        return "- `pause`/`stop` to temporarily pause status updates"
            + "\n- `unpause`/`play`/`resume` to resume status updates"
            + "\n- `purge`/`remove` to purge all user data. Fresh signup will be needed to use the app again";
    }

    @RequestMapping("/error")
    public void handleError(HttpServletResponse httpServletResponse) throws IOException {
        httpServletResponse.sendRedirect("/error.html");
    }
}
