package com.giorgimode.SpotMyStatus.model.modals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Block {

    public String type;
    public Text text;
    public Accessory accessory;
    @JsonProperty("block_id")
    public String blockId;
    public Boolean optional;
    public Element element;
    public Text label;
    public List<Element> elements;
}
