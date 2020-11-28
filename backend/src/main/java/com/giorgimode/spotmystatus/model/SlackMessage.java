package com.giorgimode.spotmystatus.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class SlackMessage {

    private final String channel;
    private final String text;
}
