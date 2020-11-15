package com.giorgimode.SpotMyStatus.helpers;

import static com.giorgimode.SpotMyStatus.util.SpotUtil.OBJECT_MAPPER;
import static org.apache.commons.lang3.StringUtils.isBlank;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giorgimode.SpotMyStatus.model.modals.SlackModalIn;
import com.giorgimode.SpotMyStatus.model.modals.SlackModalOut;
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
    public void setAsText(String text) throws IllegalArgumentException {
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