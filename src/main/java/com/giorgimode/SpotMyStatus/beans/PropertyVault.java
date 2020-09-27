package com.giorgimode.SpotMyStatus.beans;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties("secret")
@Component
@Getter
@Setter
public class PropertyVault {

    private OauthProperties slack;
    private OauthProperties spotify;
}
