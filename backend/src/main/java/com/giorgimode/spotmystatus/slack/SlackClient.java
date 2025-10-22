package com.giorgimode.spotmystatus.slack;

import static com.giorgimode.spotmystatus.helpers.SpotUtil.baseUri;

import com.giorgimode.spotmystatus.exceptions.UserNotFoundException;
import com.giorgimode.spotmystatus.helpers.RestHelper;
import com.giorgimode.spotmystatus.helpers.SpotMyStatusProperties;
import com.giorgimode.spotmystatus.persistence.User;
import com.giorgimode.spotmystatus.persistence.UserRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class SlackClient {

    private static final String MISSING_USER_ERROR = "User not found";

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final SpotMyStatusProperties configProperties;

    public SlackClient(RestTemplate restTemplate, UserRepository userRepository,
        SpotMyStatusProperties configProperties) {

        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.configProperties = configProperties;
    }

    public String notifyUser(String endpoint, Object body, String userId) {
        log.trace("Notifying user at endpoint {} with body {}", endpoint, body);
        //noinspection deprecation: Slack issues warning on missing charset
        return RestHelper.builder()
                         .withBaseUrl(configProperties.getSlackUri() + endpoint)
                         .withBearer(getUser(userId).getSlackBotToken())
                         .withContentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                         .withBody(body)
                         .postAndGetBody(restTemplate, String.class);
    }


    private User getUser(String userId) {
        return userRepository.findById(userId)
                       .orElseThrow(() -> new UserNotFoundException(MISSING_USER_ERROR));
    }

}
