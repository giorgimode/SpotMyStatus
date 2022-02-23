package com.giorgimode.spotmystatus.command;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommandMetaData {

    private Long timestamp;
    private String signature;
    private String userId;
    private String command;
    private String triggerId;
    private String body;
}
