package com.giorgimode.spotmystatus.command;

import com.giorgimode.spotmystatus.service.UserInteractionService;
import java.util.function.Function;

public class TrackUrlPrinterCommand implements Function<String, String> {

    private final UserInteractionService userInteractionService;

    public TrackUrlPrinterCommand(UserInteractionService userInteractionService) {
        this.userInteractionService = userInteractionService;
    }

    @Override
    public String apply(String userId) {
        return userInteractionService.getCurrentTracksMessage(userId);
    }
}
