package com.giorgimode.SpotMyStatus;

import com.giorgimode.SpotMyStatus.model.CachedUser;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.util.SpotUtil;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableScheduling
public class SpotMyStatusConfiguration {

    @Autowired
    private UserRepository userRepository;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        restTemplateBuilder.setReadTimeout(Duration.ofSeconds(5));
        restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(2));
        return restTemplateBuilder.build();
    }

    @Bean
    public LoadingCache<String, CachedUser> userCache() {
        LoadingCache<String, CachedUser> cache = Caffeine.newBuilder()
                                                         .maximumSize(10_000)
                                                         .build(this::loadUser);
        populateCache(cache);
        return cache;
    }


    private void populateCache(LoadingCache<String, CachedUser> cache) {
        userRepository.findAll()
                      .stream()
                      .map(SpotUtil::toCachedUser)
                      .forEach(cachedUser -> cache.put(cachedUser.getId(), cachedUser));
    }

    private CachedUser loadUser(String key) {
        return userRepository.findById(key)
                             .map(SpotUtil::toCachedUser)
                             .orElse(null);
    }
}
