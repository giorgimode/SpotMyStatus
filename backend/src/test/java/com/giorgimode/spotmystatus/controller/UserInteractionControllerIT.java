package com.giorgimode.spotmystatus.controller;

import static com.giorgimode.spotmystatus.helpers.SpotUtil.OBJECT_MAPPER;
import static com.giorgimode.spotmystatus.service.UserInteractionService.SLACK_VIEW_OPEN_URI;
import static com.giorgimode.spotmystatus.service.UserInteractionService.SLACK_VIEW_PUBLISH_URI;
import static com.giorgimode.spotmystatus.service.UserInteractionService.SLACK_VIEW_UPDATE_URI;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.giorgimode.spotmystatus.SpotMyStatusITBase;
import com.giorgimode.spotmystatus.SpotMyStatusITBase.SpotMyStatusTestConfig;
import com.giorgimode.spotmystatus.TestUtils;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.model.SlackEvent;
import com.giorgimode.spotmystatus.model.SlackEvent.Event;
import com.giorgimode.spotmystatus.model.SlackResponse;
import com.giorgimode.spotmystatus.model.modals.InteractionModal;
import com.giorgimode.spotmystatus.model.modals.InvocationModal;
import com.giorgimode.spotmystatus.persistence.User;
import com.giorgimode.spotmystatus.persistence.UserRepository;
import com.giorgimode.spotmystatus.service.UserInteractionService;
import com.giorgimode.spotmystatus.slack.SlackClient;
import com.giorgimode.spotmystatus.slack.SlackStatusPayload;
import com.giorgimode.spotmystatus.spotify.SpotifyClient;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

@AutoConfigureMockMvc
@Import(SpotMyStatusTestConfig.class)
class UserInteractionControllerIT extends SpotMyStatusITBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private UserInteractionService userInteractionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoadingCache<String, CachedUser> userCache;

    @MockBean
    private SpotifyClient spotifyClient;

    @SpyBean
    private SlackClient slackClient;

    @Test
    void shouldHandleInvalidSignature() throws Exception {
        ReflectionTestUtils.setField(userInteractionService, "shouldVerifySignature", true);
        mockMvc.perform(post("/api/slack/command")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .header("X-Slack-Request-Timestamp", "1609327004")
            .header("X-Slack-Signature", "v0=09bce6ebbc27ffecec7a351ce8efef94c83ba53cc3527384f589cb9daa0cd228_invalid")
            .queryParam("user_id", "user123")
            .content("{ \"type\": \"block_actions\", \"api_app_id\": \"test_api_id\", \"token\": \"test_token123\" }"))
               .andExpect(status().isOk())
               .andExpect(content().string("Failed to validate signature. "
                   + "If the issue persists, please contact support at https://localhost/support"));
        ReflectionTestUtils.setField(userInteractionService, "shouldVerifySignature", false);
    }

    @Test
    void shouldHandleMissingUser() throws Exception {
        mockMvc.perform(post("/api/slack/command")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .header("X-Slack-Request-Timestamp", 1609327004L)
            .header("X-Slack-Signature", "v0=09bce6ebbc27ffecec7a351ce8efef94c83ba53cc3527384f589cb9daa0cd228")
            .queryParam("user_id", "unknown_user"))
               .andExpect(status().isOk())
               .andExpect(content().string(startsWith("User not found.")));
    }

    @Test
    void shouldHandlePauseCommand() throws Exception {
        String testUserId = "user123";
        User user = userRepository.findById(testUserId).orElseThrow(AssertionFailedError::new);
        assertFalse(user.isDisabled());
        CachedUser cachedUser = userCache.get(testUserId);
        assertNotNull(cachedUser);
        assertFalse(cachedUser.isDisabled());

        SlackStatusPayload slackStatusUpdateResponse = new SlackStatusPayload();
        slackStatusUpdateResponse.setOk(true);
        when(restTemplate.postForEntity(eq("https://fake-slack.com/api/users.profile.set"), any(HttpEntity.class), eq(
            SlackStatusPayload.class))).thenReturn(new ResponseEntity<>(slackStatusUpdateResponse, HttpStatus.OK));

        mockMvc.perform(post("/api/slack/command")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .header("X-Slack-Request-Timestamp", 1609327004L)
            .header("X-Slack-Signature", "dummy_signature")
            .queryParam("user_id", testUserId)
            .queryParam("text", "pause"))
               .andExpect(status().isOk())
               .andExpect(content().string("Status updates have been paused"));

        verify(restTemplate, atLeastOnce()).postForEntity(eq("https://fake-slack.com/api/users.profile.set"), any(HttpEntity.class), eq(
            SlackStatusPayload.class));
        assertTrue(user.isDisabled());
        assertTrue(cachedUser.isDisabled());
    }

    @Test
    void shouldHandlePlayCommand() throws Exception {
        String testUserId = "user123";
        User user = userRepository.findById(testUserId).orElseThrow(AssertionFailedError::new);
        user.setDisabled(true);
        userRepository.save(user);
        CachedUser cachedUser = userCache.get(testUserId);
        assertNotNull(cachedUser);
        cachedUser.setDisabled(true);

        mockMvc.perform(post("/api/slack/command")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .header("X-Slack-Request-Timestamp", 1609327004L)
            .header("X-Slack-Signature", "dummy_signature")
            .queryParam("user_id", testUserId)
            .queryParam("text", "play"))
               .andExpect(status().isOk())
               .andExpect(content().string("Status updates have been resumed"));

        assertFalse(user.isDisabled());
        assertFalse(cachedUser.isDisabled());
    }

    @Test
    void shouldHandlePurgeCommand() throws Exception {
        SlackStatusPayload slackStatusUpdateResponse = new SlackStatusPayload();
        slackStatusUpdateResponse.setOk(true);
        when(restTemplate.postForEntity(eq("https://fake-slack.com/api/users.profile.set"), any(HttpEntity.class), eq(
            SlackStatusPayload.class))).thenReturn(new ResponseEntity<>(slackStatusUpdateResponse, HttpStatus.OK));
        String testUserId = "user123";
        mockMvc.perform(post("/api/slack/command")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .header("X-Slack-Request-Timestamp", 1609327004L)
            .header("X-Slack-Signature", "dummy_signature")
            .queryParam("user_id", testUserId)
            .queryParam("text", "purge"))
               .andExpect(status().isOk())
               .andExpect(content().string("User data has been purged. To sign up again visit the <https://localhost|app home page>"));

        verify(restTemplate).postForEntity(eq("https://fake-slack.com/api/users.profile.set"), any(HttpEntity.class), eq(
            SlackStatusPayload.class));
        assertNull(userCache.getIfPresent(testUserId));
        assertFalse(userRepository.existsById(testUserId));
    }

    @Test
    void shouldHandleRandomCommand() throws Exception {
        String testUserId = "user123";
        mockMvc.perform(post("/api/slack/command")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .header("X-Slack-Request-Timestamp", 1609327004L)
            .header("X-Slack-Signature", "dummy_signature")
            .queryParam("user_id", testUserId)
            .queryParam("text", "unknown_command"))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("`pause`/`play` to temporarily pause or resume status updates")));

        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(SlackResponse.class));
    }

    @Test
    void shouldHandleModalTrigger() throws Exception {
        doReturn("ok").when(slackClient).notifyUser(eq(SLACK_VIEW_OPEN_URI), any(InvocationModal.class), anyString());
        String testUserId = "user123";
        when(spotifyClient.getSpotifyDevices(any())).thenReturn(List.of());
        User user = userRepository.findById(testUserId).orElseThrow(AssertionFailedError::new);
        user.setDisabled(true);
        userRepository.save(user);
        CachedUser cachedUser = userCache.get(testUserId);
        assertNotNull(cachedUser);
        cachedUser.setDisabled(true);

        mockMvc.perform(post("/api/slack/command")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .header("X-Slack-Request-Timestamp", 1609327004L)
            .header("X-Slack-Signature", "dummy_signature")
            .queryParam("user_id", testUserId))
               .andExpect(status().isOk())
               .andExpect(content().string(is(emptyOrNullString())));

        verify(spotifyClient).getSpotifyDevices(any());
        verify(slackClient).notifyUser(eq(SLACK_VIEW_OPEN_URI), any(InvocationModal.class), anyString());
    }

    @Test
    void shouldHandleSlackChallenge() throws Exception {
        SlackEvent slackEvent = new SlackEvent();
        slackEvent.setType("url_verification");
        String testChallenge = "challenge123";
        slackEvent.setChallenge(testChallenge);
        mockMvc.perform(post("/api/slack/events")
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .content(OBJECT_MAPPER.writeValueAsBytes(slackEvent)))
               .andExpect(status().isOk())
               .andExpect(content().string(testChallenge));

        verifyNoInteractions(spotifyClient);
        verifyNoInteractions(slackClient);
    }

    @Test
    void shouldHandleRandomEvent() throws Exception {
        SlackEvent slackEvent = new SlackEvent();
        slackEvent.setType("unexpected");
        mockMvc.perform(post("/api/slack/events")
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .content(OBJECT_MAPPER.writeValueAsBytes(slackEvent)))
               .andExpect(status().isOk())
               .andExpect(content().string(is(emptyOrNullString())));

        verifyNoInteractions(spotifyClient);
        verifyNoInteractions(slackClient);
    }

    @Test
    void shouldHandleAppHomeOpened() throws Exception {
        doReturn("ok").when(slackClient).notifyUser(eq(SLACK_VIEW_PUBLISH_URI), any(InteractionModal.class), anyString());
        SlackEvent slackEvent = new SlackEvent();
        Event slackInnerEvent = new Event();
        slackInnerEvent.setType("app_home_opened");
        slackInnerEvent.setUser("user123");
        slackEvent.setEvent(slackInnerEvent);
        mockMvc.perform(post("/api/slack/events")
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .content(OBJECT_MAPPER.writeValueAsBytes(slackEvent)))
               .andExpect(status().isOk())
               .andExpect(content().string(is(emptyOrNullString())));

        verify(slackClient).notifyUser(eq(SLACK_VIEW_PUBLISH_URI), any(InteractionModal.class), anyString());
    }

    @Test
    void shouldHandleUserInteraction() throws Exception {
        doReturn("ok").when(slackClient).notifyUser(eq(SLACK_VIEW_UPDATE_URI), any(InteractionModal.class), anyString());
        String modalContent = TestUtils.getFileContent("files/invocation_template.json");
        InvocationModal invocationModal = OBJECT_MAPPER.readValue(modalContent, InvocationModal.class);
        mockMvc.perform(post("/api/slack/interaction")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .queryParam("payload", OBJECT_MAPPER.writeValueAsString(invocationModal)))
               .andExpect(status().isOk())
               .andExpect(content().string(is(emptyOrNullString())));

        verify(slackClient).notifyUser(eq(SLACK_VIEW_UPDATE_URI), any(InteractionModal.class), anyString());
    }
}