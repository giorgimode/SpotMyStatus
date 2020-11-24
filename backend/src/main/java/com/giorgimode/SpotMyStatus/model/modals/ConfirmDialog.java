package com.giorgimode.SpotMyStatus.model.modals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class ConfirmDialog {

    public Text title;
    public Text text;
    public Text confirm;
    public String style;

}
