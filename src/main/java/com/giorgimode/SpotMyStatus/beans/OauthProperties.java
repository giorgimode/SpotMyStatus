package com.giorgimode.SpotMyStatus.beans;

import java.net.URI;
import lombok.Data;

@Data
public class OauthProperties {

    private String clientId;
    private String clientSecret;
    private URI redirectUri;
}
