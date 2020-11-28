package com.giorgimode.spotmystatus.model.modals;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class TextObjectDeserializer extends JsonDeserializer<Object> {

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken jsonToken = p.getCurrentToken();
        if (jsonToken == JsonToken.VALUE_STRING) {
            return p.getValueAsString();
        }
        return p.readValueAs(Text.class);
    }
}
