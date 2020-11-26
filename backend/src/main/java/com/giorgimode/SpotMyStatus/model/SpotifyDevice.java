package com.giorgimode.SpotMyStatus.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SpotifyDevice {

    private String id;
    private String name;
    @JsonProperty(value = "is_private_session", required = true)
    private boolean isPrivateSession;
}
