package com.giorgimode.spotmystatus.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.giorgimode.spotmystatus.TestUtils;
import com.giorgimode.spotmystatus.model.modals.InvocationModal;
import java.io.IOException;
import org.junit.jupiter.api.Test;


class SlackModalConverterTest {


    @Test
    void shouldConvertToModal() throws IOException {
        SlackModalConverter converter = new SlackModalConverter();
        String modalContent = TestUtils.getFileContent("files/invocation_template.json");
        converter.setAsText(modalContent);
        Object value = converter.getValue();
        assertTrue(value instanceof InvocationModal);
        InvocationModal invocationModal = (InvocationModal) value;
        assertNotNull(invocationModal.getTriggerId());
        assertEquals("block_actions", invocationModal.getType());
        assertNotNull(invocationModal.getUser());
        assertNotNull(invocationModal.getView());
        assertEquals("giorgi", invocationModal.getUser().getName());
        assertEquals(1, invocationModal.getActions().size());
        assertEquals("emoji_input_block", invocationModal.getActions().get(0).getBlockId());
        assertNotNull(converter.getAsText());
    }

    @Test
    void shouldIgnoreBlankText() {
        SlackModalConverter converter = new SlackModalConverter();
        converter.setAsText("     ");
        assertNull(converter.getValue());
    }
}