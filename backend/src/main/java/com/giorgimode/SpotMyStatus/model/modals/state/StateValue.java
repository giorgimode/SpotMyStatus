package com.giorgimode.SpotMyStatus.model.modals.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.giorgimode.SpotMyStatus.model.modals.Option;
import java.util.List;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
@Data
public class StateValue {

    private String type;
    private String value;
    @JsonProperty("selected_options")
    private List<Option> selectedOptions;
}
