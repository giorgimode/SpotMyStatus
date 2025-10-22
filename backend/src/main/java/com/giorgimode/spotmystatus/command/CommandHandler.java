package com.giorgimode.spotmystatus.command;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CommandHandler {


    public CommandHandler() {

    }

    public String handleCommand() {
        return signupMessage();
    }

    private String signupMessage() {
        return "Hey there! SpotMyStatus has a new home. Sign up at <https://spotmystatus.com/|spotmystatus.com>";
    }
}
