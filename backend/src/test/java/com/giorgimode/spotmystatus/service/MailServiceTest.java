package com.giorgimode.spotmystatus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import com.giorgimode.spotmystatus.model.SubmissionForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    private MailService mailService;

    @Captor
    private ArgumentCaptor<SimpleMailMessage> mailCaptor;

    @BeforeEach
    void setUp() {
        mailService = new MailService(javaMailSender);
        ReflectionTestUtils.setField(mailService, "senderEmail", "sender@test.com");
        ReflectionTestUtils.setField(mailService, "recipientEmail", "recipient@test.com");
    }

    @Test
    void sendEmail() {
        mailService.sendEmail(new SubmissionForm("plumbus", "test@test.com", "something serious", "I'm in trouble"));
        verify(javaMailSender).send(mailCaptor.capture());
        SimpleMailMessage sentMessage = mailCaptor.getValue();
        assertNotNull(sentMessage);
        assertEquals("sender@test.com", sentMessage.getFrom());
        assertNotNull(sentMessage.getTo());
        assertEquals("recipient@test.com", sentMessage.getTo()[0]);
        assertEquals("something serious", sentMessage.getSubject());
        assertEquals("Name: plumbus\nEmail: test@test.com\n\nI'm in trouble", sentMessage.getText());

    }
}