package com.giorgimode.spotmystatus.model.modals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class ModalView {

    private String id;
    private String hash;
    private String type = "modal";

    @JsonProperty("callback_id")
    private String callbackId;
    private Text title;
    private Text submit;
    private List<Block> blocks;
    private State state;
}
