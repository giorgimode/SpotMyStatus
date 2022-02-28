package com.giorgimode.spotmystatus.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.giorgimode.spotmystatus.SpotMyStatusITBase;
import com.giorgimode.spotmystatus.SpotMyStatusITBase.SpotMyStatusTestConfig;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.model.SlackResponse;
import com.giorgimode.spotmystatus.model.SlackToken;
import com.giorgimode.spotmystatus.model.SlackToken.SlackTokenPayload;
import com.giorgimode.spotmystatus.model.SlackToken.Team;
import com.giorgimode.spotmystatus.model.SpotifyTokenResponse;
import com.giorgimode.spotmystatus.persistence.User;
import com.giorgimode.spotmystatus.persistence.UserRepository;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@AutoConfigureMockMvc
@Import(SpotMyStatusTestConfig.class)
class AuthorizationControllerIT extends SpotMyStatusITBase {

    private static final String TEST_SLACK_ACCESS_TOKEN = "slack_access_token_456";
    private static final String TEST_SLACK_BOT_TOKEN = "slack_bot_token_456";

    @Autowired
    private LoadingCache<String, CachedUser> userCache;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldStartAuthorization() throws Exception {
        String expectedLocation = "https://fake-slack.com/oauth/v2/authorize?scope=chat:write,commands"
            + "&user_scope=users:read,users.profile:read,users.profile:write"
            + "&redirect_uri=https://localhost/api/slack/redirect&client_id=slack_client123";
        mockMvc.perform(get("/api/start/"))
               .andExpect(status().is(302))
               .andExpect(header().string("Location", expectedLocation));
    }

    @Test
    void shouldRedirectToErrorPageOnMissingSlackCode() throws Exception {
        mockMvc.perform(get("/api/slack/redirect"))
               .andExpect(status().is(302))
               .andExpect(redirectedUrl("/error"));
    }

    @Test
    void shouldAuthorizeUserInSlack() throws Exception {
        String newUserId = "new_user123";
        authorizeInSlack(newUserId);

        Optional<User> createdUser = userRepository.findById(newUserId);
        assertTrue(createdUser.isPresent());
        assertEquals(TEST_SLACK_ACCESS_TOKEN, createdUser.get().getSlackAccessToken());
        assertEquals(TEST_SLACK_BOT_TOKEN, createdUser.get().getSlackBotToken());
        assertEquals(3600, createdUser.get().getTimezoneOffsetSeconds());
        assertNotNull(createdUser.get().getState());
        assertNotNull(createdUser.get().getCreatedAt());
    }

    private MvcResult authorizeInSlack(String newUserId) throws Exception {
        mockSlackAuth(newUserId);
        mockSlackUserInfo();

        String spotifyAuthLocation = "https://fake-spotify.com/authorize?"
            + "scope=user-read-playback-state"
            + "&response_type=code&redirect_uri=https://localhost/api/spotify/redirect"
            + "&state=*&client_id=spotify_client123";
        return mockMvc.perform(get("/api/slack/redirect")
            .queryParam("code", "slack_code_123"))
                      .andExpect(status().is(302))
                      .andExpect(redirectedUrlPattern(spotifyAuthLocation))
                      .andReturn();
    }

    @Test
    void shouldNotAuthorizeUserInSlackWithoutAccessToken() throws Exception {
        String newUserId = "new_user123";
        SlackToken slackToken = mockSlackAuth(newUserId);
        slackToken.getAuthUser().setAccessToken(null);
        mockMvc.perform(get("/api/slack/redirect")
            .queryParam("code", "slack_code_123"))
               .andExpect(status().isUnauthorized());

        assertFalse(userRepository.existsById(newUserId));
    }


    private void mockSlackUserInfo() {
        SlackResponse slackResponse = new SlackResponse();
        slackResponse.setTimezoneOffset(3600);
        when(restTemplate.exchange(eq("https://fake-slack.com/api/users.info?user=new_user123"), eq(HttpMethod.GET), any(HttpEntity.class),
            eq(SlackResponse.class))).thenReturn(new ResponseEntity<>(slackResponse, HttpStatus.OK));
    }

    private SlackToken mockSlackAuth(String newUserId) {
        SlackToken slackToken = new SlackToken();
        SlackTokenPayload slackTokenPayload = new SlackTokenPayload();
        slackTokenPayload.setAccessToken(TEST_SLACK_ACCESS_TOKEN);
        slackToken.setBotToken(TEST_SLACK_BOT_TOKEN);
        slackTokenPayload.setId(newUserId);
        Team team = new Team();
        team.setId("team_123");
        slackToken.setTeam(team);
        slackToken.setAuthUser(slackTokenPayload);
        String slackOauthUri = "https://fake-slack.com/api/oauth.v2.access?"
            + "code=slack_code_123"
            + "&client_secret=slack_client_secret123"
            + "&redirect_uri=https://localhost/api/slack/redirect"
            + "&client_id=slack_client123";
        when(restTemplate.exchange(eq(slackOauthUri), eq(HttpMethod.GET), any(HttpEntity.class),
            eq(SlackToken.class))).thenReturn(new ResponseEntity<>(slackToken, HttpStatus.OK));
        return slackToken;
    }

    @Test
    void shouldRedirectToErrorPageOnMissingSpotifyCode() throws Exception {
        mockMvc.perform(get("/api/spotify/redirect"))
               .andExpect(status().is(302))
               .andExpect(redirectedUrl("/error"));
    }

    @Test
    void shouldAuthorizeUserInSpotify() throws Exception {
        String testUserId = "new_user123";
        String teamId = "team_123";
        CachedUser cachedUser = CachedUser.builder()
                                          .id(testUserId)
                                          .teamId(teamId)
                                          .slackAccessToken("old_slack_token")
                                          .slackBotToken("old_slack_bot_token")
                                          .spotifyRefreshToken("old_spotify_refresh_token")
                                          .spotifyAccessToken("old_spotify_access_token")
                                          .timezoneOffsetSeconds(7200)
                                          .syncStartHour(900)
                                          .syncEndHour(1100)
                                          .build();
        cachedUser.setSlackStatus("Swans - Power and Sacrifice");
        userCache.put(testUserId, cachedUser);

        MvcResult mvcResult = authorizeInSlack(testUserId);
        String location = mvcResult.getResponse().getHeader("Location");
        assertNotNull(location);
        MultiValueMap<String, String> queryParams = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        String stateValue = queryParams.getFirst("state");

        String spotifyRefreshToken = "spotify_refresh_token123";
        SpotifyTokenResponse spotifyTokenResponse = new SpotifyTokenResponse("test", 0, spotifyRefreshToken);
        String testSpotifyAccessToken = "spotify_access_token123";
        spotifyTokenResponse.setAccessToken(testSpotifyAccessToken);
        when(restTemplate.postForEntity(eq("https://fake-spotify.com/api/token"), any(HttpEntity.class), eq(SpotifyTokenResponse.class)))
            .thenReturn(new ResponseEntity<>(spotifyTokenResponse, HttpStatus.OK));

        mockMvc.perform(get("/api/spotify/redirect")
            .queryParam("code", "spotify_code_123")
            .queryParam("state", stateValue))
               .andExpect(status().is(302))
               .andExpect(redirectedUrl("/success"));

        Optional<User> createdUser = userRepository.findById(testUserId);
        assertTrue(createdUser.isPresent());
        assertEquals(TEST_SLACK_ACCESS_TOKEN, createdUser.get().getSlackAccessToken());
        assertEquals(TEST_SLACK_BOT_TOKEN, createdUser.get().getSlackBotToken());
        assertEquals(spotifyRefreshToken, createdUser.get().getSpotifyRefreshToken());
        assertFalse(createdUser.get().isDisabled());
        assertEquals(3600, createdUser.get().getTimezoneOffsetSeconds());
        assertNotNull(createdUser.get().getState());
        assertNotNull(createdUser.get().getCreatedAt());

        CachedUser newCachedUser = userCache.getIfPresent(testUserId);
        assertNotNull(newCachedUser);
        assertEquals(testUserId, newCachedUser.getId());
        assertEquals(3600, newCachedUser.getTimezoneOffsetSeconds());
        assertEquals("Swans - Power and Sacrifice", newCachedUser.getSlackStatus());
        assertEquals(TEST_SLACK_ACCESS_TOKEN, newCachedUser.getSlackAccessToken());
        assertEquals(TEST_SLACK_BOT_TOKEN, newCachedUser.getSlackBotToken());
        assertEquals(testSpotifyAccessToken, newCachedUser.getSpotifyAccessToken());
        assertEquals(spotifyRefreshToken, newCachedUser.getSpotifyRefreshToken());
    }
}