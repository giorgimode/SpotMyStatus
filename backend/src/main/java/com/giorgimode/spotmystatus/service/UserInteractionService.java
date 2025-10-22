package com.giorgimode.spotmystatus.service;

import com.giorgimode.spotmystatus.model.modals.InteractionModal;
import com.giorgimode.spotmystatus.model.modals.ModalView;
import com.giorgimode.spotmystatus.slack.SlackClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static com.giorgimode.spotmystatus.helpers.SpotUtil.OBJECT_MAPPER;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Component
@Slf4j
public class UserInteractionService {

    public static final String SLACK_VIEW_PUBLISH_URI = "/api/views.publish";

    private final SlackClient slackClient;

    @Value("classpath:templates/slack_modal_view_template.json")
    private Resource resourceFile;

    public UserInteractionService(SlackClient slackClient) {

        this.slackClient = slackClient;
    }

    private ModalView getModalViewTemplate() {
        try {
            return OBJECT_MAPPER.readValue(resourceFile.getInputStream(), ModalView.class);
        } catch (IOException e) {
            log.error("Failed to create modal view template", e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR);
        }
    }


    public void updateHomeTab(String userId) {
        InteractionModal homeModal = new InteractionModal();
        homeModal.setUserId(userId);
        ModalView modalView = new ModalView();
        modalView.setType("home");
        homeModal.setView(modalView);
        modalView.setBlocks(getModalViewTemplate().getBlocks());
        String response = slackClient.notifyUser(SLACK_VIEW_PUBLISH_URI, homeModal, userId);
        log.trace("Slack returned response when updating home tab {}", response);
    }

}
