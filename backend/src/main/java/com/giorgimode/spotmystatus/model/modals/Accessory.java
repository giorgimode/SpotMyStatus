package com.giorgimode.spotmystatus.model.modals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
@Data
public class Accessory {

    private String type;
    private List<Option> options;

    @JsonProperty("action_id")
    private String actionId;

    @JsonProperty("initial_options")
    private List<Option> initialOptions;

    private Text placeholder;
}
