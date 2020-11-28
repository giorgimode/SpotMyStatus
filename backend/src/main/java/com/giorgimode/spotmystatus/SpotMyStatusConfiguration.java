package com.giorgimode.spotmystatus;

import com.giorgimode.spotmystatus.helpers.PropertyVault;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.model.SpotifyTokenResponse;
import com.giorgimode.spotmystatus.persistence.User;
import com.giorgimode.spotmystatus.persistence.UserRepository;
import com.giorgimode.spotmystatus.service.CleanupService;
import com.giorgimode.spotmystatus.spotify.SpotifyAuthClient;
import com.giorgimode.spotmystatus.helpers.SpotUtil;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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


    @Bean
    public CleanupService cleanupService(PropertyVault propertyVault) {
        return new CleanupService(userRepository, propertyVault, restTemplate(null));
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        restTemplateBuilder.setReadTimeout(Duration.ofSeconds(5));
        restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(2));
        return restTemplateBuilder.build();
    }

    @Bean
    public ThreadPoolExecutor cachedThreadPool(@Value("${core_pool_size}") Integer corePoolSize) {
        return new ThreadPoolExecutor(corePoolSize, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
    }

    @Bean
    public LoadingCache<String, CachedUser> userCache(SpotifyAuthClient spotifyAuthClient) {
        CleanupService cleanupService = cleanupService(null);
        LoadingCache<String, CachedUser> cache = Caffeine.newBuilder()
                                                         .maximumSize(10_000)
                                                         .build(key -> loadUser(key, spotifyAuthClient, cleanupService));
        populateCache(cache, spotifyAuthClient, cleanupService);
        return cache;
    }


    private void populateCache(LoadingCache<String, CachedUser> cache, SpotifyAuthClient spotifyAuthClient, CleanupService cleanupService) {
        userRepository.findAll()
                      .stream()
                      .map(user -> cacheUser(spotifyAuthClient, user, cleanupService))
                      .filter(Objects::nonNull)
                      .forEach(cachedUser -> cache.put(cachedUser.getId(), cachedUser));
    }

    private String getAccessToken(SpotifyAuthClient spotifyAuthClient, User user) {
        SpotifyTokenResponse newAccessToken = spotifyAuthClient.getNewAccessToken(user.getSpotifyRefreshToken());
        log.info("Retrieved spotify access token expiring in {} seconds", newAccessToken.getExpiresIn());
        return newAccessToken.getAccessToken();
    }

    private CachedUser loadUser(String key, SpotifyAuthClient spotifyAuthClient, CleanupService cleanupService) {
        return userRepository.findById(key)
                             .map(user -> cacheUser(spotifyAuthClient, user, cleanupService))
                             .orElse(null);
    }

    private CachedUser cacheUser(SpotifyAuthClient spotifyAuthClient, User user, CleanupService cleanupService) {
        try {
            return SpotUtil.toCachedUser(user, getAccessToken(spotifyAuthClient, user));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST && ex.getResponseBodyAsString().contains("invalid_grant")) {
                log.error("User's spotify token has been invalidated. Removing the user");
                cleanupService.invalidateAndNotifyUser(user.getId()); //todo temp fix for circular dependency
            } else {
                log.error("Failed to cache user with id {}", user.getId(), ex);
            }
        } catch (Exception e) {
            log.error("Failed to cache user with id {}", user.getId(), e);
        }
        return null;
    }
}
