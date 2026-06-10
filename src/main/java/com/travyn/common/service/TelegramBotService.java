package com.travyn.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class TelegramBotService {

    private final RestTemplate restTemplate;

    @Value("${telegram.bot.token}")
    private String botToken;

    public TelegramBotService() {
        this.restTemplate = new RestTemplate();
    }

    public void sendMessage(Long chatId, String text) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Telegram bot token not configured. Cannot send message to {}", chatId);
            return;
        }

        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", text,
                "parse_mode", "Markdown",
                "disable_web_page_preview", true
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Telegram message sent successfully to chat {}", chatId);
            } else {
                log.error("Failed to send Telegram message to chat {}: {}", chatId, response.getBody());
            }
        } catch (Exception e) {
            log.error("Error sending Telegram message to chat {}: {}", chatId, e.getMessage());
        }
    }

    public void sendSOSAlert(Long chatId, String travelerName, Double lat, Double lng, String trackingUrl) {
        String googleMapsUrl = (lat != null && lng != null) 
                ? "https://maps.google.com/?q=" + lat + "," + lng 
                : "Unknown Location";

        String message = String.format(
                "🚨 *EMERGENCY SOS ALERT* 🚨\n\n" +
                "*%s* hasn't responded to safety checks on Travyn and might be in danger.\n\n" +
                "📍 *Last Known Location:*\n%s\n\n" +
                "🗺️ *Live Tracking & Details:*\n%s\n\n" +
                "Please check on them immediately!",
                travelerName, googleMapsUrl, trackingUrl
        );

        sendMessage(chatId, message);
    }
}
