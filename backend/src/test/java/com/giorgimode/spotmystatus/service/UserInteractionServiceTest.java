package com.giorgimode.spotmystatus.service;

import static com.giorgimode.spotmystatus.helpers.SpotUtil.OBJECT_MAPPER;
import static com.giorgimode.spotmystatus.service.UserInteractionService.SLACK_VIEW_OPEN_URI;
import static com.giorgimode.spotmystatus.service.UserInteractionService.SLACK_VIEW_PUBLISH_URI;
import static com.giorgimode.spotmystatus.service.UserInteractionService.SLACK_VIEW_UPDATE_URI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.giorgimode.spotmystatus.TestUtils;
import com.giorgimode.spotmystatus.helpers.OauthProperties;
import com.giorgimode.spotmystatus.helpers.PropertyVault;
import com.giorgimode.spotmystatus.helpers.SpotMyStatusProperties;
import com.giorgimode.spotmystatus.model.CachedUser;
import com.giorgimode.spotmystatus.model.SpotifyItem;
import com.giorgimode.spotmystatus.model.modals.InteractionModal;
import com.giorgimode.spotmystatus.model.modals.InvocationModal;
import com.giorgimode.spotmystatus.persistence.User;
import com.giorgimode.spotmystatus.persistence.UserRepository;
import com.giorgimode.spotmystatus.slack.SlackClient;
import com.giorgimode.spotmystatus.spotify.SpotifyClient;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserInteractionServiceTest {


    private static final String TEST_USER_ID = "user1";
    private LoadingCache<String, CachedUser> userCache;
    private CachedUser cachedUser;
    private PropertyVault propertyVault;
    private UserInteractionService userInteractionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SlackClient slackClient;

    @Mock
    private SpotifyClient spotifyClient;

    @Mock
    private Resource resourceFile;

    @Captor
    private ArgumentCaptor<InvocationModal> invocationModalCaptor;

    @Captor
    private ArgumentCaptor<InteractionModal> interactionModalCaptor;


    @BeforeEach
    void setUp() {
        userCache = Caffeine.newBuilder()
                            .maximumSize(10_000)
                            .build(key -> createCachedUser());

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setContextPath("/spotmystatus-context");
        ServletRequestAttributes attrs = new ServletRequestAttributes(mockRequest);
        RequestContextHolder.setRequestAttributes(attrs);

        SpotMyStatusProperties spotMyStatusProperties = new SpotMyStatusProperties();
        spotMyStatusProperties.setDefaultSpotifyItems(Map.of("track", "Music", "episode", "Podcast"));
        spotMyStatusProperties.setDefaultEmojis(List.of("headphones", "musical_note", "notes"));
        spotMyStatusProperties.setRedirectUriScheme("https");
        propertyVault = new PropertyVault();
        userInteractionService = new UserInteractionService(userRepository, spotMyStatusProperties, userCache, slackClient, spotifyClient,
            propertyVault);

        cachedUser = createCachedUser();
        ReflectionTestUtils.setField(userInteractionService, "resourceFile", resourceFile);
        ReflectionTestUtils.setField(userInteractionService, "shouldVerifySignature", true);
    }

    @Test
    void userShouldBeMissing() {
        assertTrue(userInteractionService.isUserMissing("userX"));
    }

    @Test
    void userShouldBePresent() {
        userCache.put(TEST_USER_ID, cachedUser);
        assertFalse(userInteractionService.isUserMissing(TEST_USER_ID));
    }

    @Test
    void shouldHandleTrigger() throws IOException {
        mockTemplateResource();
        cachedUser.setDisabled(true);
        cachedUser.setSpotifyItems(List.of(SpotifyItem.TRACK));
        userInteractionService.handleTrigger(TEST_USER_ID, "trigger123");
        verify(slackClient).notifyUser(eq(SLACK_VIEW_OPEN_URI), invocationModalCaptor.capture(), eq(TEST_USER_ID));
        InvocationModal sentModal = invocationModalCaptor.getValue();
        assertEquals("trigger123", sentModal.getTriggerId());
        assertNotNull(sentModal.getView());
        assertNotNull(sentModal.getView().getType());
        assertNotNull(sentModal.getView().getSubmit());
        assertEquals(14, sentModal.getView().getBlocks().size());
    }

    @Test
    void shouldThrow500OnFailedTemplate() throws IOException {
        when(resourceFile.getInputStream()).thenReturn(new ByteArrayInputStream("garbage_data".getBytes(StandardCharsets.UTF_8)));
        assertThrows(ResponseStatusException.class, () -> userInteractionService.handleTrigger(TEST_USER_ID, "trigger123"));
    }

    @Test
    void shouldHandleUserBlockAction() throws IOException {
        String modalContent = TestUtils.getFileContent("files/invocation_template.json");
        InvocationModal invocationModal = OBJECT_MAPPER.readValue(modalContent, InvocationModal.class);
        InteractionModal modal = userInteractionService.handleUserInteraction(invocationModal);
        assertNull(modal);
        verify(slackClient).notifyUser(eq(SLACK_VIEW_UPDATE_URI), interactionModalCaptor.capture(), eq("test_user_id"));
        InteractionModal updateModal = interactionModalCaptor.getValue();
        assertNotNull(updateModal.getView());
        assertNotNull(updateModal.getViewId());
        assertNotNull(updateModal.getHash());
        assertNotNull(updateModal.getView().getType());
        assertNotNull(updateModal.getView().getSubmit());
        assertEquals(14, updateModal.getView().getBlocks().size());
    }

    @Test
    void shouldHandleSubmission() throws IOException {
        mockTemplateResource();
        User storedUser = new User();
        storedUser.setId(TEST_USER_ID);
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(storedUser));
        String modalContent = TestUtils.getFileContent("files/submission_template.json");
        InvocationModal invocationModal = OBJECT_MAPPER.readValue(modalContent, InvocationModal.class);
        InteractionModal modal = userInteractionService.handleUserInteraction(invocationModal);
        assertNull(modal);
        verify(slackClient).notifyUser(eq(SLACK_VIEW_PUBLISH_URI), interactionModalCaptor.capture(), eq(TEST_USER_ID));
        InteractionModal sentHomeTab = interactionModalCaptor.getValue();
        assertEquals(TEST_USER_ID, sentHomeTab.getUserId());
        assertNotNull(sentHomeTab.getView());
        assertEquals("home", sentHomeTab.getView().getType());
        assertNull(sentHomeTab.getViewId());
        assertNull(sentHomeTab.getHash());
        assertNotNull(sentHomeTab.getView().getType());
        assertNull(sentHomeTab.getView().getSubmit());
        assertEquals(16, sentHomeTab.getView().getBlocks().size());
        assertEquals(List.of("guitar"), cachedUser.getEmojis());
        assertEquals(List.of(SpotifyItem.TRACK), cachedUser.getSpotifyItems());
        assertTrue(cachedUser.getSpotifyDeviceIds().containsAll(List.of("echoDotId123", "macbookDeviceId123")));
        verify(slackClient).resume(TEST_USER_ID);
        verify(userRepository).findById(TEST_USER_ID);
        verify(userRepository).save(storedUser);
        assertNotNull(storedUser.toString());
        assertEquals("guitar", storedUser.getEmojis());
        assertEquals("track", storedUser.getSpotifyItems());
        assertEquals("macbookDeviceId123,echoDotId123", storedUser.getSpotifyDevices());
    }

    @Test
    void shouldSkipValidatingSignature() {
        ReflectionTestUtils.setField(userInteractionService, "shouldVerifySignature", false);
        assertTrue(userInteractionService.isValidSignature(null, null, null));
    }

    @Test
    void shouldValidateSignature() {
        ReflectionTestUtils.setField(userInteractionService, "shouldVerifySignature", true);
        OauthProperties slackVault = new OauthProperties();
        slackVault.setSigningSecret("signing_secret_123");
        propertyVault.setSlack(slackVault);
        assertTrue(userInteractionService.isValidSignature(1609327004L, "v0=5e26f6dd3afeb452b6faab9e9269c6e6c76716c5f8b7664c9661d0c6fb800add",
            "{ \"type\": \"block_actions\", \"api_app_id\": \"test_api_id\", \"token\": \"test_token123\" }"));
    }

    @Test
    void shouldNotValidateSignature() {
        ReflectionTestUtils.setField(userInteractionService, "shouldVerifySignature", true);
        OauthProperties slackVault = new OauthProperties();
        slackVault.setSigningSecret("signing_secret_123");
        propertyVault.setSlack(slackVault);
        assertFalse(userInteractionService.isValidSignature(1609327004L, "v0=5e26f6dd3afeb452b6faab9e9269c6e6c76716c5f8b7664c9661d0c6fb800addX",
            "{ \"type\": \"block_actions\", \"api_app_id\": \"test_api_id\", \"token\": \"test_token123\" }"));
    }


    @Test
    void pause() {
        userInteractionService.pause(TEST_USER_ID);
        verify(slackClient).pause(TEST_USER_ID);
    }

    @Test
    void resume() {
        userInteractionService.resume(TEST_USER_ID);
        verify(slackClient).resume(TEST_USER_ID);
    }

    @Test
    void purge() {
        userInteractionService.purge(TEST_USER_ID);
        verify(slackClient).purge(TEST_USER_ID);
    }

    @Test
    void shouldUpdateHomeTabForUnknownUser() {
        userInteractionService.updateHomeTab("unknown_user");
        verifyNoInteractions(slackClient);
    }

    private CachedUser createCachedUser() {
        CachedUser cachedUser = CachedUser.builder()
                                          .id(TEST_USER_ID)
                                          .slackAccessToken("testSlackToken")
                                          .slackBotToken("testSlackNotToken")
                                          .spotifyRefreshToken("testSpotifyRefreshToken")
                                          .spotifyAccessToken("testSpotifyAccessToken")
                                          .build();
        userCache.put(TEST_USER_ID, cachedUser);
        return cachedUser;
    }

    private void mockTemplateResource() throws IOException {
        String fileContent = TestUtils.getFileContent("templates/slack_modal_view_template.json");
        when(resourceFile.getInputStream()).thenReturn(new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)));
    }
}