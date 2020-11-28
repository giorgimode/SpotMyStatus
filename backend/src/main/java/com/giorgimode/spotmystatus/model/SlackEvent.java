package com.giorgimode.spotmystatus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlackEvent {

    private String token;
    private String challenge;
    private String type;
    private String event;
}
