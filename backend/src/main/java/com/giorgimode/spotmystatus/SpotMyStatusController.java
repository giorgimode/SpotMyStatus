package com.giorgimode.spotmystatus;

import static com.giorgimode.spotmystatus.helpers.SpotConstants.SLACK_REDIRECT_PATH;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.SPOTIFY_REDIRECT_PATH;
import static org.apache.commons.lang3.StringUtils.isBlank;
import com.giorgimode.spotmystatus.helpers.SlackModalConverter;
import com.giorgimode.spotmystatus.model.SlackEvent;
import com.giorgimode.spotmystatus.model.modals.SlackModalIn;
import com.giorgimode.spotmystatus.model.modals.SlackModalOut;
import com.giorgimode.spotmystatus.service.UserInteractionService;
import com.giorgimode.spotmystatus.slack.SlackClient;
import com.giorgimode.spotmystatus.spotify.SpotifyClient;
import java.io.IOException;
import java.util.UUID;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class SpotMyStatusController {

    private static final String ERROR_PAGE = "/error.html";

    @Autowired
    private SpotifyClient spotifyClient;

    @Autowired
    private SlackClient slackPollingClient;

    @Autowired
    private UserInteractionService userInteractionService;

    @RequestMapping("/start")
    public void startNewUser(HttpServletResponse httpServletResponse) {
        log.info("Starting authorization for a new user...");
        String authorization = slackPollingClient.requestAuthorization();
        httpServletResponse.setHeader("Location", authorization);
        httpServletResponse.setStatus(302);
    }

    @RequestMapping(SLACK_REDIRECT_PATH)
    public void slackRedirect(@RequestParam(value = "code", required = false) String slackCode,
        @RequestParam(value = "error", required = false) String error,
        HttpServletResponse httpServletResponse) throws IOException {

        if (slackCode == null) {
            log.info("User failed to grant permission on Slack. Received error {}", error);
            httpServletResponse.sendRedirect(ERROR_PAGE);
            return;
        }

        log.info("User has granted permission on Slack. Received code {}", slackCode);
        UUID state = slackPollingClient.updateAuthToken(slackCode);

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
        httpServletResponse.sendRedirect("/success.html");
    }

    @PostMapping(value = "/slack/command", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String receiveSlackCommand(
        @RequestHeader("X-Slack-Request-Timestamp") Long timestamp,
        @RequestHeader("X-Slack-Signature") String signature,
        @RequestParam("user_id") String userId,
        @RequestParam(value = "text", required = false) String command,
        @RequestParam(value = "trigger_id", required = false) String triggerId,
        @RequestBody String bodyString) {

        log.trace("Received a slack command {}", bodyString);
        slackPollingClient.validateSignature(timestamp, signature, bodyString);
        if (isBlank(command)) {
            userInteractionService.handleTrigger(userId, triggerId);
            return null;
        }
        if ("pause".equalsIgnoreCase(command)) {
            log.debug("Pausing updates for user {}", userId);
            return slackPollingClient.pause(userId);
        }
        if ("play".equalsIgnoreCase(command)) {
            log.debug("Resuming updates for user {}", userId);
            return slackPollingClient.resume(userId);
        }
        if ("purge".equalsIgnoreCase(command)) {
            log.debug("Removing all data for user {}", userId);
            return slackPollingClient.purge(userId);
        }

        return "- `pause`/`play` to temporarily pause or resume status updates"
            + "\n- `purge` to purge all user data. Fresh signup will be needed to use the app again";
    }

    @PostMapping(value = "/slack/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String receiveSlackEvent(@RequestBody SlackEvent slackEvent) {
        log.debug("Received a slack event {}", slackEvent);
        return slackEvent.getChallenge();
    }

    @RequestMapping("/error")
    public void handleError(HttpServletResponse httpServletResponse) throws IOException {
        httpServletResponse.sendRedirect(ERROR_PAGE);
    }

    @PostMapping(value = "/slack/interaction", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public SlackModalOut handleInteraction(@RequestParam("payload") SlackModalIn payload, @RequestParam("payload") String payloadRaw) {
        log.debug("Received interaction: {}", payloadRaw);
        return userInteractionService.handleUserInteraction(payload);
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(SlackModalIn.class, new SlackModalConverter());
    }
}
