package com.travyn.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travyn.notification.dto.PushSubscriptionRequest;
import com.travyn.notification.entity.PushSubscription;
import com.travyn.notification.repository.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebPushService {

    private final PushSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    @Value("${vapid.public.key:BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuB23T0w5g5Z6x3q8zX2w6k0w4=}")
    private String publicKey;

    @Value("${vapid.private.key:W2vY3O7o8S0H-M_G1v4-z8E6O9vY6Q9u0S0H-M_G1v4=}")
    private String privateKey;

    @Value("${vapid.subject:mailto:admin@travyn.com}")
    private String subject;

    private PushService pushService;

    @PostConstruct
    public void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            pushService = new PushService(publicKey, privateKey, subject);
        } catch (Exception e) {
            log.error("Failed to initialize PushService", e);
        }
    }

    public String getPublicKey() {
        return publicKey;
    }

    @Transactional
    public void subscribe(UUID userId, PushSubscriptionRequest request) {
        Optional<PushSubscription> existing = subscriptionRepository.findByEndpoint(request.getEndpoint());
        if (existing.isPresent()) {
            PushSubscription sub = existing.get();
            sub.setUserId(userId);
            sub.setP256dh(request.getP256dh());
            sub.setAuth(request.getAuth());
            subscriptionRepository.save(sub);
        } else {
            PushSubscription sub = PushSubscription.builder()
                    .userId(userId)
                    .endpoint(request.getEndpoint())
                    .p256dh(request.getP256dh())
                    .auth(request.getAuth())
                    .build();
            subscriptionRepository.save(sub);
        }
    }

    @Transactional
    public void unsubscribe(String endpoint) {
        subscriptionRepository.deleteByEndpoint(endpoint);
    }

    public void sendPushNotification(UUID userId, String title, String body, String url) {
        if (pushService == null) return;
        
        List<PushSubscription> subscriptions = subscriptionRepository.findByUserId(userId);
        if (subscriptions.isEmpty()) return;

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", title,
                    "body", body,
                    "url", url != null ? url : "/dashboard",
                    "icon", "/favicon.ico"
            ));

            for (PushSubscription sub : subscriptions) {
                try {
                    Notification notification = new Notification(
                            sub.getEndpoint(),
                            sub.getP256dh(),
                            sub.getAuth(),
                            payload
                    );
                    nl.martijndwars.webpush.HttpResponse response = pushService.send(notification);
                    if (response.getStatus() == 410 || response.getStatus() == 404) {
                        // Gone, remove subscription
                        unsubscribe(sub.getEndpoint());
                    }
                } catch (Exception e) {
                    log.error("Failed to send push notification to endpoint {}", sub.getEndpoint(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize push payload", e);
        }
    }
}
