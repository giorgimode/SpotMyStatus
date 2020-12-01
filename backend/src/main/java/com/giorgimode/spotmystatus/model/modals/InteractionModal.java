package com.giorgimode.spotmystatus.model.modals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class InteractionModal {


    @JsonProperty("view_id")
    private String viewId;
    private String hash;
    private ModalView view;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("response_action")
    private String responseAction;
}
