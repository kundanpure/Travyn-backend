package com.travyn.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Sends transactional emails via Brevo's HTTP API (no SMTP ports needed).
 * Activated only in production when brevo.api-key is set.
 */
@Component
@ConditionalOnProperty(name = "brevo.api-key")
@Slf4j
public class BrevoEmailSender {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    @Value("${brevo.api-key}")
    private String apiKey;

    @Value("${brevo.sender-email:codekundan01@gmail.com}")
    private String senderEmail;

    @Value("${brevo.sender-name:Travyn}")
    private String senderName;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Send an HTML email via Brevo's transactional HTTP API.
     *
     * @param to          recipient email address
     * @param subject     email subject
     * @param htmlContent full HTML body
     */
    public void send(String to, String subject, String htmlContent) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);

            Map<String, Object> body = Map.of(
                    "sender", Map.of("name", senderName, "email", senderEmail),
                    "to", List.of(Map.of("email", to)),
                    "subject", subject,
                    "htmlContent", htmlContent
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email sent via Brevo to: {}", to);
            } else {
                log.error("Brevo API returned {}: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to send email via Brevo to {}: {}", to, e.getMessage());
        }
    }
}
