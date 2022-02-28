package com.giorgimode.spotmystatus.command;

import static com.giorgimode.spotmystatus.helpers.SpotUtil.baseUri;
import com.giorgimode.spotmystatus.service.UserInteractionService;
import com.giorgimode.spotmystatus.slack.SlackClient;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PurgeCommand implements Function<String, String> {

    private final SlackClient slackClient;
    private final UserInteractionService userInteractionService;

    public PurgeCommand(SlackClient slackClient, UserInteractionService userInteractionService) {
        this.slackClient = slackClient;
        this.userInteractionService = userInteractionService;
    }

    @Override
    public String apply(String userId) {
        log.debug("Removing all data for user {}", userId);
        userInteractionService.updateHomeTabForMissingUser(userId);
        return slackClient.purge(userId) + signupMessage();
    }

    private String signupMessage() {
        return String.format("To sign up again, visit the <%s|app home page>", baseUri());
    }
}
