package com.travyn.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Sends email via Brevo's HTTP API.
 * This is used in production environments to bypass Render's SMTP port block.
 */
@Service
@ConditionalOnExpression("'${brevo.api-key:}' != ''")
@Slf4j
public class BrevoEmailSender {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    @Value("${brevo.api-key}")
    private String apiKey;

    @Value("${brevo.sender-email:kundankumar8797737@gmail.com}")
    private String senderEmail;

    @Value("${brevo.sender-name:Travyn}")
    private String senderName;

    private final RestTemplate restTemplate;

    public BrevoEmailSender() {
        this.restTemplate = new RestTemplate();
    }

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
                log.info("Email sent successfully via Brevo to: {}", to);
            } else {
                log.error("Failed to send email via Brevo to {}: {}", to, response.getBody());
            }

        } catch (Exception e) {
            log.error("Error sending email via Brevo to {}: {}", to, e.getMessage());
        }
    }
}
