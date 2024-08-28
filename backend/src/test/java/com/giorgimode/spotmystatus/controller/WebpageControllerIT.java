package com.giorgimode.spotmystatus.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.giorgimode.spotmystatus.SpotMyStatusITBase;
import com.giorgimode.spotmystatus.SpotMyStatusITBase.SpotMyStatusTestConfig;
import com.giorgimode.spotmystatus.model.SubmissionForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

@Disabled
@AutoConfigureMockMvc
@Import(SpotMyStatusTestConfig.class)
class WebpageControllerIT extends SpotMyStatusITBase {

    private MockMvc mockMvc;

    @Autowired
    private WebpageController webpageController;

    @Captor
    private ArgumentCaptor<SimpleMailMessage> mailCaptor;

    @BeforeEach
    void setUp() {
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setSuffix(".html");
        mockMvc = MockMvcBuilders.standaloneSetup(webpageController)
                                 .setViewResolvers(viewResolver)
                                 .build();
    }

    @Test
    void shouldHandleSupport() throws Exception {
        String testSubject = "Mastering Invisibility";
        SubmissionForm submissionForm = new SubmissionForm("Drax", "drax@thedestroyer.com", testSubject,
            "I'm invisible");
        mockMvc.perform(post("/support/")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .flashAttr("submissionForm", submissionForm))
               .andExpect(status().isOk())
               .andExpect(forwardedUrl("support.html"))
               .andExpect(model().attribute("formResult", "success"))
               .andReturn();

        verify(mailSender).send(mailCaptor.capture());
        SimpleMailMessage sentMessage = mailCaptor.getValue();
        assertNotNull(sentMessage);
        assertEquals("sender@test.com", sentMessage.getFrom());
        assertNotNull(sentMessage.getTo());
        assertEquals("recipient@test.com", sentMessage.getTo()[0]);
        assertEquals(testSubject, sentMessage.getSubject());
        assertEquals("Name: Drax\nEmail: drax@thedestroyer.com\n\nI'm invisible", sentMessage.getText());
    }

    @Test
    void shouldReturnSupportFailResult() throws Exception {
        doThrow(new RuntimeException()).when(mailSender).send(any(SimpleMailMessage.class));
        mockMvc.perform(post("/support/")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
               .andExpect(status().isOk())
               .andExpect(forwardedUrl("support.html"))
               .andExpect(model().attribute("formResult", "fail"))
               .andReturn();

        verify(mailSender).send(mailCaptor.capture());
    }

    @Test
    void shouldGetSupportPage() throws Exception {
        shouldResolveToHtmlPage("support");
    }

    @Test
    void shouldGetSuccessPage() throws Exception {
        shouldResolveToHtmlPage("success");
    }

    @Test
    void shouldGetPrivacyPage() throws Exception {
        shouldResolveToHtmlPage("privacy");
    }

    @Test
    void shouldGetTermsAndConditionsPage() throws Exception {
        shouldResolveToHtmlPage("terms");
    }

    @Test
    void shouldHandleError() throws Exception {
        shouldResolveToHtmlPage("error");
    }

    private void shouldResolveToHtmlPage(final String pageName) throws Exception {
        mockMvc.perform(get("/" + pageName))
               .andExpect(status().isOk())
               .andExpect(forwardedUrl(pageName + ".html"))
               .andReturn();
    }
}