package com.giorgimode.SpotMyStatus.model.modals;

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

    public String type;
    @JsonDeserialize(using = TextObjectDeserializer.class)
    public Object text;
    @JsonProperty("action_id")
    public String actionId;
    public Text placeholder;
    @JsonProperty("initial_time")
    public String initialTime;
    public ConfirmDialog confirm;
    public String style;
    @JsonProperty("initial_options")
    public List<Option> initialOptions;
    public List<Option> options;
}
