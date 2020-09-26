package com.giorgimode.SpotMyStatus;

import com.wrapper.spotify.SpotifyApi;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpotMyStatusApplication {

    @Value("${secret.spotify.client_id}")
    private String spotifyClientId;

    @Value("${secret.spotify.client_secret}")
    private String spotifyClientSecret;

    public static void main(String[] args) {
        SpringApplication.run(SpotMyStatusApplication.class, args);
    }

    @Bean
    public SpotifyApi spotifyApi() {
        return new SpotifyApi.Builder()
            .setClientId(spotifyClientId)
            .setClientSecret(spotifyClientSecret)
            .setRedirectUri(URI.create(spotifyClientSecret))
            .build();
    }
}
