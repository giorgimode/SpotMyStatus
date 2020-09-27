package com.giorgimode.SpotMyStatus.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
public class SpotifyTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    private final String scope;

    @JsonProperty("expires_in")
    private final String expiresIn;

    @JsonProperty("refresh_token")
    private final String refreshToken;
}
