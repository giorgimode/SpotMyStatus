package com.giorgimode.SpotMyStatus.service;

import static org.mockito.Mockito.verify;
import com.giorgimode.SpotMyStatus.common.PropertyVault;
import com.giorgimode.SpotMyStatus.model.CachedUser;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.slack.SlackPollingClient;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PropertyVault propertyVault;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private LoadingCache<String, CachedUser> userCache;

    @InjectMocks
    private UserInteractionService notificationService;

    @Mock
    private SlackPollingClient slackPollingClient;


    @BeforeEach
    void setUp() {

    }

    @Test
    void shouldInvalidateAndNotifyUser() {
        String userId = "test_user123";
        notificationService.invalidateAndNotifyUser(userId);
        verify(slackPollingClient).invalidateAndNotifyUser(userId);
    }
}