package com.giorgimode.SpotMyStatus.common;

import javax.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class OauthProperties {

    @NotEmpty
    private String clientId;

    @NotEmpty
    private String clientSecret;

    private String botToken;
    private String signingSecret;
}
