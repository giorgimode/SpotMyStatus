package com.giorgimode.SpotMyStatus;

import com.wrapper.spotify.SpotifyApi;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class SpotMyStatusConfiguration {

    @Value("${secret.spotify.client_id}")
    private String spotifyClientId;

    @Value("${secret.spotify.client_secret}")
    private String spotifyClientSecret;

    @Bean
    public SpotifyApi spotifyApi() {
        return new SpotifyApi.Builder()
            .setClientId(spotifyClientId)
            .setClientSecret(spotifyClientSecret)
            .setRedirectUri(URI.create("http://localhost:8080/redirect"))
            .build();
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        restTemplateBuilder.setReadTimeout(Duration.ofSeconds(5));
        restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(2));
        return restTemplateBuilder.build();
    }
}
