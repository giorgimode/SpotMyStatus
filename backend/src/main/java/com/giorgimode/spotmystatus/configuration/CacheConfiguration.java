package com.giorgimode.spotmystatus.configuration;

import com.giorgimode.spotmystatus.helpers.SpotUtil;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.model.SpotifyTokenResponse;
import com.giorgimode.spotmystatus.persistence.User;
import com.giorgimode.spotmystatus.persistence.UserRepository;
import com.giorgimode.spotmystatus.spotify.SpotifyAuthClient;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@Configuration
@Slf4j
public class CacheConfiguration {

    @Bean
    public LoadingCache<String, CachedUser> userCache(SpotifyAuthClient spotifyAuthClient, UserRepository userRepository) {
        LoadingCache<String, CachedUser> cache = Caffeine.newBuilder()
                                                         .maximumSize(10_000)
                                                         .build(key -> loadUser(key, spotifyAuthClient, userRepository));
        populateCache(cache, spotifyAuthClient, userRepository);
        return cache;
    }


    private void populateCache(LoadingCache<String, CachedUser> cache, SpotifyAuthClient spotifyAuthClient, UserRepository userRepository) {
        userRepository.findAll()
                      .stream()
                      .map(user -> cacheUser(spotifyAuthClient, user, userRepository))
                      .filter(Objects::nonNull)
                      .forEach(cachedUser -> cache.put(cachedUser.getId(), cachedUser));
    }

    private String getAccessToken(SpotifyAuthClient spotifyAuthClient, User user) {
        SpotifyTokenResponse newAccessToken = spotifyAuthClient.getNewAccessToken(user.getSpotifyRefreshToken());
        log.info("Retrieved spotify access token expiring in {} seconds", newAccessToken.getExpiresIn());
        return newAccessToken.getAccessToken();
    }

    private CachedUser loadUser(String userId,
        SpotifyAuthClient spotifyAuthClient,
        UserRepository userRepository) {

        return userRepository.findById(userId)
                             .map(user -> cacheUser(spotifyAuthClient, user, userRepository))
                             .orElse(null);
    }

    private CachedUser cacheUser(SpotifyAuthClient spotifyAuthClient, User user, UserRepository userRepository) {
        try {
            return SpotUtil.toCachedUser(user, getAccessToken(spotifyAuthClient, user));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST && ex.getResponseBodyAsString().contains("invalid_grant")) {
                log.error("User's spotify token has been invalidated. Removing the user");
                userRepository.deleteById(user.getId());
            } else {
                log.error("Failed to cache user with id {}", user.getId(), ex);
            }
        } catch (Exception e) {
            log.error("Failed to cache user with id {}", user.getId(), e);
        }
        return null;
    }
}
