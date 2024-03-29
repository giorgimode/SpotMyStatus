
package com.giorgimode.spotmystatus.model.modals;

import static com.giorgimode.spotmystatus.helpers.SpotConstants.ACTION_END_HOUR;
import static com.giorgimode.spotmystatus.helpers.SpotConstants.ACTION_START_HOUR;
import static com.giorgimode.spotmystatus.helpers.SpotUtil.OBJECT_MAPPER;
import static com.giorgimode.spotmystatus.helpers.SpotUtil.safeGet;
import static java.util.stream.Collectors.toMap;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
@Data
public class State {

    private Map<String, StateValue> stateValues;

    @JsonProperty("values")
    private void unpackNested(Map<String, Object> values) {
        if (values == null) {
            return;
        }
        stateValues = values.entrySet()
                            .stream()
                            .collect(toMap(Entry::getKey, this::collectValues));
    }

    @SuppressWarnings("unchecked")
    private StateValue collectValues(Map.Entry<String, Object> value) {
        Map<String, Object> valueMap = (Map<String, Object>) value.getValue();
        if (valueMap.containsKey(ACTION_START_HOUR)) {
            return createHourStateValue(valueMap);
        } else {
            return valueMap.values().stream().findFirst()
                           .map(map -> (Map<String, Object>) map)
                           .map(this::createOptionsStateValue)
                           .orElseGet(StateValue::new);
        }
    }

    private StateValue createOptionsStateValue(Map<String, Object> firstValue) {
        StateValue stateValue = new StateValue();
        String type = safeGet(firstValue, "type");
        stateValue.setType(type);
        stateValue.setValue(safeGet(firstValue, "value"));
        List<Object> optionsList = safeGet(firstValue, "selected_options", List.of());
        List<Option> selectedOptions = OBJECT_MAPPER.convertValue(optionsList, new TypeReference<>() {
        });
        stateValue.setSelectedOptions(selectedOptions);
        return stateValue;
    }

    @SuppressWarnings("unchecked")
    private StateValue createHourStateValue(Map<String, Object> valueMap) {
        StateValue stateValue = new StateValue();
        String startHour = safeGet(((Map<String, Object>) valueMap.get(ACTION_START_HOUR)), "selected_time");
        String endHour = safeGet(((Map<String, Object>) valueMap.get(ACTION_END_HOUR)), "selected_time");
        stateValue.setType("timepicker");
        stateValue.setStartHour(startHour);
        stateValue.setEndHour(endHour);
        return stateValue;
    }

}
