package com.giorgimode.spotmystatus.controller;

import static com.giorgimode.spotmystatus.helpers.SpotUtil.baseUri;
import static org.apache.commons.lang3.StringUtils.isBlank;
import com.giorgimode.spotmystatus.helpers.SlackModalConverter;
import com.giorgimode.spotmystatus.helpers.SpotMyStatusProperties;
import com.giorgimode.spotmystatus.model.SlackEvent;
import com.giorgimode.spotmystatus.model.modals.InteractionModal;
import com.giorgimode.spotmystatus.model.modals.InvocationModal;
import com.giorgimode.spotmystatus.service.UserInteractionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class UserInteractionController {

    private final UserInteractionService userInteractionService;
    private final SpotMyStatusProperties configProperties;

    public UserInteractionController(UserInteractionService userInteractionService,
        SpotMyStatusProperties configProperties) {
        this.userInteractionService = userInteractionService;
        this.configProperties = configProperties;
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
        userInteractionService.validateSignature(timestamp, signature, bodyString);
        if (!userInteractionService.userExists(userId)) {
            return "User not found. Please sign up at " + baseUri(configProperties.getRedirectUriScheme()) + "/start";
        }

        if (isBlank(command)) {
            userInteractionService.handleTrigger(userId, triggerId);
            return null;
        }
        if ("pause".equalsIgnoreCase(command)) {
            log.debug("Pausing updates for user {}", userId);
            return userInteractionService.pause(userId);
        }
        if ("play".equalsIgnoreCase(command)) {
            log.debug("Resuming updates for user {}", userId);
            return userInteractionService.resume(userId);
        }
        if ("purge".equalsIgnoreCase(command)) {
            log.debug("Removing all data for user {}", userId);
            return userInteractionService.purge(userId) + signupMessage();
        }

        return "- `pause`/`play` to temporarily pause or resume status updates"
            + "\n- `purge` to purge all user data. Fresh signup will be needed to use the app again"
            + "\n- " + signupMessage();
    }

    private String signupMessage() {
        return "Visit the app home page at " + baseUri(configProperties.getRedirectUriScheme());
    }

    @PostMapping(value = "/slack/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String receiveSlackEvent(@RequestBody SlackEvent slackEvent) {
        log.debug("Received a slack event {}", slackEvent);
        return slackEvent.getChallenge();
    }

    @PostMapping(value = "/slack/interaction", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public InteractionModal handleInteraction(@RequestParam("payload") InvocationModal payload, @RequestParam("payload") String payloadRaw) {
        log.debug("Received interaction: {}", payloadRaw);
        return userInteractionService.handleUserInteraction(payload);
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(InvocationModal.class, new SlackModalConverter());
    }
}
