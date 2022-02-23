package com.giorgimode.spotmystatus.controller;

import static com.giorgimode.spotmystatus.helpers.SpotUtil.OBJECT_MAPPER;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.giorgimode.spotmystatus.command.CommandHandler;
import com.giorgimode.spotmystatus.command.CommandMetaData;
import com.giorgimode.spotmystatus.helpers.SlackModalConverter;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api")
@Slf4j
public class UserInteractionController {

    private final UserInteractionService userInteractionService;
    private final CommandHandler commandHandler;

    public UserInteractionController(UserInteractionService userInteractionService, CommandHandler commandHandler) {
        this.userInteractionService = userInteractionService;
        this.commandHandler = commandHandler;
    }

    @PostMapping(value = "/slack/command", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String receiveSlackCommand(
        @RequestHeader("X-Slack-Request-Timestamp") Long timestamp,
        @RequestHeader("X-Slack-Signature") String signature,
        @RequestParam("user_id") String userId,
        @RequestParam(value = "text", required = false) String command,
        @RequestParam(value = "trigger_id", required = false) String triggerId,
        @RequestBody String body) {

        log.trace("Received a slack command {}", body);
        CommandMetaData commandMetaData = CommandMetaData.builder()
                                                         .body(body)
                                                         .signature(signature)
                                                         .command(command)
                                                         .triggerId(triggerId)
                                                         .userId(userId)
                                                         .timestamp(timestamp)
                                                         .build();
        return commandHandler.handleCommand(commandMetaData);
    }

    @PostMapping(value = "/slack/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String receiveSlackEvent(@RequestBody String rawBody) throws JsonProcessingException {
        log.trace("Received a slack event {}", rawBody);
        SlackEvent slackEvent = OBJECT_MAPPER.readValue(rawBody, SlackEvent.class);
        if ("url_verification".equals(slackEvent.getType())) {
            return slackEvent.getChallenge();
        } else if ("app_home_opened".equals(slackEvent.getEventType())) {
            userInteractionService.updateHomeTab(slackEvent.getUser());
        }
        return null;
    }

    @PostMapping(value = "/slack/interaction", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public InteractionModal handleInteraction(@RequestParam("payload") InvocationModal payload, @RequestParam("payload") String payloadRaw) {
        log.trace("Received interaction: {}", payloadRaw);
        return userInteractionService.handleUserInteraction(payload);
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(InvocationModal.class, new SlackModalConverter());
    }
}
