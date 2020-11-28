package com.giorgimode.spotmystatus.model.modals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Block {

    private String type;
    private Text text;
    private Accessory accessory;

    @JsonProperty("block_id")
    private String blockId;
    private Boolean optional;

    @JsonProperty("dispatch_action")
    private Boolean dispatchAction;

    private Element element;
    private Text label;
    private List<Element> elements;
}
