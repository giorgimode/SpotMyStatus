package com.giorgimode.spotmystatus.model.modals;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.giorgimode.spotmystatus.model.modals.ModalView;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class AppHomeModal {

    @JsonProperty("user_id")
    private final String userId;
    private final ModalView view;
}
