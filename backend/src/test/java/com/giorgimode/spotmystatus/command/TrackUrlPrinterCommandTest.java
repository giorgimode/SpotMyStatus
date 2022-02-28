package com.giorgimode.spotmystatus.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import com.giorgimode.spotmystatus.service.UserInteractionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrackUrlPrinterCommandTest {

    @Mock
    private  UserInteractionService userInteractionService;

    @InjectMocks
    private TrackUrlPrinterCommand trackUrlPrinterCommand;

    @Test
    void shouldApply() {
        String userId = "userId123";
        String resultMessage = "Success";
        when(userInteractionService.getCurrentTracksMessage(userId)).thenReturn(resultMessage);
        String response = trackUrlPrinterCommand.apply(userId);
        assertEquals(resultMessage, response);
    }
}