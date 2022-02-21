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
public class StateValue {

    private String type;
    private String value;
    private String startHour;
    private String endHour;

    @JsonProperty("selected_options")
    private List<Option> selectedOptions;
}
