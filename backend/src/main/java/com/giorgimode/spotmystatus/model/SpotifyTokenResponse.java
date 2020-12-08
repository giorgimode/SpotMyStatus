package com.giorgimode.spotmystatus.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SpotifyTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    private final String scope;

    @JsonProperty("expires_in")
    private final Integer expiresIn;

    @JsonProperty("refresh_token")
    private final String refreshToken;
}
