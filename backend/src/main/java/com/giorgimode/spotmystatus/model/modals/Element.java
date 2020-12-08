package com.giorgimode.spotmystatus.model.modals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Element {

    private String type;

    @JsonDeserialize(using = TextObjectDeserializer.class)
    private Object text;

    @JsonProperty("action_id")
    private String actionId;
    private Text placeholder;

    @JsonProperty("initial_time")
    private String initialTime;

    private ConfirmDialog confirm;
    private String style;

    @JsonProperty("initial_options")
    private List<Option> initialOptions;
    private List<Option> options;
}
