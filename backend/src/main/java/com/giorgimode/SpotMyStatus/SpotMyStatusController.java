package com.giorgimode.SpotMyStatus;

import static com.giorgimode.SpotMyStatus.common.SpotConstants.SLACK_REDIRECT_PATH;
import static com.giorgimode.SpotMyStatus.common.SpotConstants.SPOTIFY_REDIRECT_PATH;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import com.giorgimode.SpotMyStatus.helpers.SlackModalConverter;
import com.giorgimode.SpotMyStatus.model.SlackEvent;
import com.giorgimode.SpotMyStatus.model.modals.SlackModalIn;
import com.giorgimode.SpotMyStatus.model.modals.SlackModalOut;
import com.giorgimode.SpotMyStatus.slack.SlackClient;
import com.giorgimode.SpotMyStatus.spotify.SpotifyClient;
import com.giorgimode.SpotMyStatus.util.RestHelper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

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

    @Autowired
    private RestTemplate restTemplate;

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
            log.info("User failed to grant permission on Slack. Received error {}", error);
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
        //todo if no command, display modal, otherwise accept command
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

    @PostMapping(value = "/slack/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String receiveSlackEvent(@RequestBody SlackEvent slackEvent) {

        log.debug("Received a slack event {}", slackEvent);

        return slackEvent.getChallenge();
    }

    @RequestMapping("/error")
    public void handleError(HttpServletResponse httpServletResponse) throws IOException {
        httpServletResponse.sendRedirect("/error.html");
    }

    @PostMapping(value = "/slack/modal", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void handleModalTrigger(@RequestParam("trigger_id") String triggerId,
        @RequestParam("user_id") String userId) {

        SlackModalIn slackModalIn = new SlackModalIn();
        slackModalIn.setTriggerId(triggerId);
        String response = notifyUser(slackModalIn, "open").getBody();
        log.trace("Received response: {}", response);
    }

    public ResponseEntity<String> notifyUser(Object body, final String viewAction) {
        return RestHelper.builder()
                         .withBaseUrl("https://slack.com/api/views." + viewAction)
                         .withBearer("my_bearer")
                         .withContentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                         .withBody(body)
                         .post(restTemplate, String.class);
    }

    @PostMapping(value = "/slack/interaction", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void handleInteraction(@RequestParam("payload") SlackModalIn payload) {
        log.debug("Received interaction: {}", payload);

        SlackModalOut slackModal = new SlackModalOut();
        slackModal.setViewId(payload.getView().getId());
        slackModal.setHash(payload.getView().getHash());
        slackModal.setView(payload.getView());
        slackModal.getView().setHash(null);
        slackModal.getView().setId(null);
        //todo perhaps start from scratch with template here
        //todo add actions to the SlackModalIn
        String response = notifyUser(slackModal, "update").getBody();
        log.debug("Received modal update response: {}", response);
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(SlackModalOut.class, new SlackModalConverter());
    }
}
