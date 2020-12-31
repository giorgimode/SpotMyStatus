package com.giorgimode.spotmystatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.giorgimode.spotmystatus.model.SlackResponse;
import com.giorgimode.spotmystatus.model.SpotifyTokenResponse;
import java.util.concurrent.ExecutorService;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class SpotMyStatusITBase {

    @MockBean
    protected JavaMailSender mailSender;

    @MockBean
    protected ClientRegistrationRepository clientRegistrationRepository;

    @TestConfiguration
    public static class SpotMyStatusTestConfig {

        @Bean
        public RestTemplate restTemplate() {
            RestTemplate restTemplate = mock(RestTemplate.class);
            mockSpotifyAuthCall(restTemplate);
            mockSlackPresenceCall(restTemplate);
            return restTemplate;
        }

        private void mockSlackPresenceCall(RestTemplate restTemplate) {
            SlackResponse slackPresenceResponse = new SlackResponse();
            slackPresenceResponse.setPresence("active");
            when(restTemplate.exchange(eq("https://fake-slack.com/api/users.getPresence"), eq(HttpMethod.GET), any(HttpEntity.class), eq(
                SlackResponse.class))).thenReturn(new ResponseEntity<>(slackPresenceResponse, HttpStatus.OK));
        }

        @Bean
        public ExecutorService cachedThreadPool() {
            ExecutorService executor = mock(ExecutorService.class);
            doAnswer(
                (InvocationOnMock invocation) -> {
                    ((Runnable) invocation.getArguments()[0]).run();
                    return null;
                }
            ).when(executor).execute(any(Runnable.class));
            return executor;
        }
    }

    private static void mockSpotifyAuthCall(RestTemplate restTemplate) {
        SpotifyTokenResponse spotifyTokenResponse = new SpotifyTokenResponse("test", 0, "spotify_refresh_token123");
        spotifyTokenResponse.setAccessToken("spotify_access_token123");
        when(restTemplate.postForEntity(eq("https://fake-spotify.com/api/token"), any(HttpEntity.class), eq(SpotifyTokenResponse.class)))
            .thenReturn(new ResponseEntity<>(spotifyTokenResponse, HttpStatus.OK));
    }
}
