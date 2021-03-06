package com.giorgimode.spotmystatus.helpers;

import javax.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class OauthProperties {

    @NotEmpty
    private String clientId;

    @NotEmpty
    private String clientSecret;
    private String signingSecret;
}
