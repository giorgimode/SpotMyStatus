package com.giorgimode.SpotMyStatus.model.modals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class User {

    @JsonProperty("id")
    public String id;
    @JsonProperty("username")
    public String username;
    @JsonProperty("name")
    public String name;
    @JsonProperty("team_id")
    public String teamId;

}