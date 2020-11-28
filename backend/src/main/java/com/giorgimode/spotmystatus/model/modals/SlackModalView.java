package com.giorgimode.spotmystatus.model.modals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class SlackModalView {

    public String id;
    public String hash;
    public String type = "modal";
    @JsonProperty("callback_id")
    public String callbackId;
    public Text title;
    public Text submit;
    public List<Block> blocks;
    private State state;
}
