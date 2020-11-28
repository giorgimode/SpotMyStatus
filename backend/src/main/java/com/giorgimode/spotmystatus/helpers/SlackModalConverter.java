package com.giorgimode.spotmystatus.helpers;

import static com.giorgimode.spotmystatus.helpers.SpotUtil.OBJECT_MAPPER;
import static org.apache.commons.lang3.StringUtils.isBlank;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.giorgimode.spotmystatus.model.modals.SlackModalIn;
import java.beans.PropertyEditorSupport;
import lombok.SneakyThrows;

public class SlackModalConverter extends PropertyEditorSupport {

    @SneakyThrows
    @Override
    public String getAsText() {
        SlackModalIn slackModal = (SlackModalIn) getValue();
        return slackModal == null ? "" : OBJECT_MAPPER.writeValueAsString(slackModal);
    }

    @Override
    public void setAsText(String text) {
        if (isBlank(text)) {
            setValue(null);
        } else {
            try {
                setValue(OBJECT_MAPPER.readValue(text, SlackModalIn.class));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }
}