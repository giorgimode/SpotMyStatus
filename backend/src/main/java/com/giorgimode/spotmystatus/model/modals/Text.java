package com.giorgimode.spotmystatus.model.modals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Text {

    private String type;

    @JsonProperty("text")
    private String textValue;

    private Boolean emoji;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Text other = (Text) o;
        return Objects.equals(type, other.type) &&
            Objects.equals(textValue, other.textValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, textValue);
    }
}
