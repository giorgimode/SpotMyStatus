package com.giorgimode.spotmystatus.helpers;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties("spotmystatus")
@Component
@Getter
@Setter
public class SpotMyStatusProperties {

    private String redirectUriScheme;
    private String slackUri;
    private String spotifyAccountUri;
    private String spotifyApiUri;
    private Long pollingRate;
    private Integer minSleepOnApiRateExceeded;
    private Integer syncStartHr;
    private Integer syncEndHr;
    private Integer timeout;
    private Integer expirationOverhead;
    private Boolean shutdownCleanupEnabled;
    private List<String> defaultEmojis;
    private String podcastEmoji;
    private Map<String, String> defaultSpotifyItems;
}
