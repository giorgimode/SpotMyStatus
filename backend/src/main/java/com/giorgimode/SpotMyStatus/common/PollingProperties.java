package com.giorgimode.SpotMyStatus.common;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties("spotmystatus")
@Component
@Getter
@Setter
public class PollingProperties {

    private Integer passivePollingProbability;
    private Integer passivateAfterMin;
    private Integer passivateStartHr;
    private Integer passivateEndHr;
    private Integer timeout;
    private List<String> emojis;
}
