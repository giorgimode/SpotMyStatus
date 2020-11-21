package com.giorgimode.SpotMyStatus.service;

import com.giorgimode.SpotMyStatus.common.PropertyVault;
import com.giorgimode.SpotMyStatus.model.SlackMessage;
import com.giorgimode.SpotMyStatus.persistence.UserRepository;
import com.giorgimode.SpotMyStatus.util.RestHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class CleanupService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PropertyVault propertyVault;

    @Value("${slack_uri}")
    private String slackUri;

    @Value("${sign_up_uri}")
    private String signupUri;

    @Autowired
    private RestTemplate restTemplate;

    public void invalidateAndNotifyUser(String userId) {
        try {
            userRepository.deleteById(userId);
            RestHelper.builder()
                      .withBaseUrl(slackUri + "/api/chat.postMessage")
                      .withBearer(propertyVault.getSlack().getBotToken())
                      .withContentType(MediaType.APPLICATION_JSON_VALUE)
                      .withBody(new SlackMessage(userId, createNotificationText()))
                      .post(restTemplate, String.class);
        } catch (Exception e) {
            log.error("Failed to clean up user properly", e);
        }
    }

    private String createNotificationText() {
        return "Spotify token has been invalidated. Please authorize again <" + signupUri + "|here>";
    }
}
