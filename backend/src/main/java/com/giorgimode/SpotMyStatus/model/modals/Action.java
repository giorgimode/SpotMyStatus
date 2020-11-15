package com.giorgimode.SpotMyStatus.model.modals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Action {

    @JsonProperty("action_id")
    public String actionId;
    @JsonDeserialize(using = TextObjectDeserializer.class)
    public Object text;
}
