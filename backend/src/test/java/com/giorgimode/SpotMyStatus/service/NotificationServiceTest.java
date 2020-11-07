package com.giorgimode.SpotMyStatus.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.giorgimode.SpotMyStatus.common.OauthProperties;
import com.giorgimode.SpotMyStatus.common.PropertyVault;
import com.giorgimode.SpotMyStatus.model.CachedUser;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
//@RunWith(JUnitPlatform.class)
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
    private NotificationService notificationService;


    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "slackUri", "http://localhost:8080");
    }

    @Test
    void shouldInvalidateAndNotifyUser() {
        String userId = "test_user123";
        when(propertyVault.getSlack()).thenReturn(new OauthProperties());
        notificationService.invalidateAndNotifyUser(userId);
        verify(userCache).invalidate(userId);
        verify(userRepository).deleteById(userId);
        verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), any());
    }
}