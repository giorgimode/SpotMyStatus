package com.giorgimode.SpotMyStatus.model.modals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class SlackModalIn {

    @JsonProperty("trigger_id")
    public String triggerId;
    public String type;
    public User user;
    public SlackModalView view;
    public List<Action> actions;
}
