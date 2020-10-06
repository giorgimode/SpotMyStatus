package com.giorgimode.SpotMyStatus;

import com.giorgimode.SpotMyStatus.model.CachedUser;
import com.giorgimode.SpotMyStatus.model.SpotifyTokenResponse;
import com.giorgimode.SpotMyStatus.persistence.User;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.service.NotificationService;
import com.giorgimode.SpotMyStatus.spotify.SpotifyAuthClient;
import com.giorgimode.SpotMyStatus.util.SpotUtil;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableScheduling
@Slf4j
public class SpotMyStatusConfiguration {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService cleanupService;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        restTemplateBuilder.setReadTimeout(Duration.ofSeconds(5));
        restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(2));
        return restTemplateBuilder.build();
    }

    @Bean
    public LoadingCache<String, CachedUser> userCache(SpotifyAuthClient spotifyAuthClient) {
        LoadingCache<String, CachedUser> cache = Caffeine.newBuilder()
                                                         .maximumSize(10_000)
                                                         .build(key -> loadUser(key, spotifyAuthClient));
        populateCache(cache, spotifyAuthClient);
        return cache;
    }


    private void populateCache(LoadingCache<String, CachedUser> cache, SpotifyAuthClient spotifyAuthClient) {
        userRepository.findAll()
                      .stream()
                      .map(user -> cacheUser(spotifyAuthClient, user))
                      .filter(Objects::nonNull)
                      .forEach(cachedUser -> cache.put(cachedUser.getId(), cachedUser));
    }

    private String getAccessToken(SpotifyAuthClient spotifyAuthClient, User user) {
        SpotifyTokenResponse newAccessToken = spotifyAuthClient.getNewAccessToken(user.getSpotifyRefreshToken());
        log.info("Retrieved spotify access token {} expiring in {} seconds", newAccessToken.getAccessToken(), newAccessToken.getExpiresIn());
        return newAccessToken.getAccessToken();
    }

    private CachedUser loadUser(String key, SpotifyAuthClient spotifyAuthClient) {
        return userRepository.findById(key)
                             .map(user -> cacheUser(spotifyAuthClient, user))
                             .orElse(null);
    }

    private CachedUser cacheUser(SpotifyAuthClient spotifyAuthClient, User user) {
        try {
            return SpotUtil.toCachedUser(user, getAccessToken(spotifyAuthClient, user));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST && ex.getResponseBodyAsString().contains("invalid_grant")) {
                log.error("User's spotify token has been invalidated. Removing the user");
                cleanupService.invalidateAndNotifyUser(user.getId());
            } else {
                log.error("Failed to cache user with id {}", user.getId(), ex);
            }
        } catch (Exception e) {
            log.error("Failed to cache user with id {}", user.getId(), e);
        }
        return null;
    }
}
