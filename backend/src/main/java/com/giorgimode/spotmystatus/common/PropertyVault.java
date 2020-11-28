package com.giorgimode.spotmystatus.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("secret")
@Component
@Getter
@Setter
@Validated
public class PropertyVault {

    private OauthProperties slack;
    private OauthProperties spotify;
}
