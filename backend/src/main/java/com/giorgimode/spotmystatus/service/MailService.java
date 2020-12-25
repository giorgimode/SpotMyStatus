package com.giorgimode.spotmystatus.service;

import com.giorgimode.spotmystatus.model.SubmissionForm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private final JavaMailSender emailSender;

    @Value("${email.sender}")
    private String senderEmail;

    @Value("${email.recipient}")
    private String recipientEmail;

    public MailService(JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void sendEmail(SubmissionForm submissionForm) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject(submissionForm.getSubject());
        message.setText(String.format("Name: %s%nEmail: %s%n%n%s", submissionForm.getName(), submissionForm.getEmail(), submissionForm.getMessage()));
        emailSender.send(message);
    }
}