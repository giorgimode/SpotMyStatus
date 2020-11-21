
package com.giorgimode.SpotMyStatus.model.modals.state;

import static com.giorgimode.SpotMyStatus.util.SpotUtil.OBJECT_MAPPER;
import static com.giorgimode.SpotMyStatus.util.SpotUtil.safeGet;
import static java.util.stream.Collectors.toMap;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.giorgimode.SpotMyStatus.model.modals.Option;
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
                            .collect(toMap(Entry::getKey, e -> collectValues(getFirstValue(e))));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getFirstValue(Entry<String, Object> e) {
        return (Map<String, Object>) ((Map<String, Object>) e.getValue()).values().stream().findFirst().orElse(Map.of());
    }

    private StateValue collectValues(Map<String, Object> valueMap) {
        StateValue stateValue = new StateValue();
        stateValue.setType(safeGet(valueMap, "type"));
        stateValue.setValue(safeGet(valueMap, "value"));
        List<Object> optionsList = safeGet(valueMap, "selected_options", List.of());
        List<Option> selectedOptions = OBJECT_MAPPER.convertValue(optionsList, new TypeReference<>() {
        });
        stateValue.setSelectedOptions(selectedOptions);

        return stateValue;
    }

}
