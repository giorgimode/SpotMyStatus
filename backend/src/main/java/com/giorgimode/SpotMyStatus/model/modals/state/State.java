
package com.giorgimode.SpotMyStatus.model.modals.state;

import static com.giorgimode.SpotMyStatus.util.SpotUtil.safeGet;
import static java.util.stream.Collectors.toMap;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
@Data
public class State {

    private Map<String, StateValue> stateValues;

    @SuppressWarnings("unchecked")
    @JsonProperty("values")
    private void unpackNested(Map<String, Object> values) {
        if (values == null) {
            return;
        }
        stateValues = values.entrySet().stream()
                            .collect(toMap(Entry::getKey,
                                // will fail if block contains more than 1 action
                                e -> collectValues(((Map<String, Object>) e.getValue()).entrySet().iterator().next()))
                            );
    }

    @SuppressWarnings("unchecked")
    private StateValue collectValues(Map.Entry<String, Object> stateValueEntry) {
        Map<String, Object> valueMap = (Map<String, Object>) stateValueEntry.getValue();
        StateValue stateValue = new StateValue();
        stateValue.setType(safeGet(valueMap, "type"));
        stateValue.setValue(safeGet(valueMap, "value"));
        List<Map<String, Object>> selectedOptions = safeGet(valueMap, "selected_options", List.of());
        List<String> values = selectedOptions.stream()
                                             .map(stringObjectMap -> (String) safeGet(stringObjectMap, "value"))
                                             .collect(Collectors.toList());
        stateValue.setSelectedValues(values);
        return stateValue;
    }

}
