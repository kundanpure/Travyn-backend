package com.travyn.chat.config;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.auth.security.JwtUtil;
import com.travyn.trip.entity.MemberStatus;
import com.travyn.trip.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intercepts STOMP CONNECT frames to validate JWT tokens and
 * SUBSCRIBE frames to enforce topic-level authorization.
 *
 * Trip topics (/topic/chat/{tripId}, /topic/map/{tripId}/...)
 *   → user must be an APPROVED member of the trip.
 *
 * User topics (/topic/user.{userId}.dm.*, /topic/user.{userId}.notifications)
 *   → the {userId} in the topic must match the authenticated user's own ID.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final TripMemberRepository tripMemberRepository;

    // Matches: /topic/chat/{uuid} or /topic/map/{uuid} or /topic/map/{uuid}/locations etc.
    private static final Pattern TRIP_TOPIC_PATTERN =
            Pattern.compile("^/topic/(?:chat|map)/([a-f0-9\\-]+)(?:/.*)?$", Pattern.CASE_INSENSITIVE);

    // Matches: /topic/user.{uuid}.dm.messages, /topic/user.{uuid}.dm.read-receipts, /topic/user.{uuid}.notifications
    private static final Pattern USER_TOPIC_PATTERN =
            Pattern.compile("^/topic/user\\.([a-f0-9\\-]+)\\..*$", Pattern.CASE_INSENSITIVE);

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        }

        return message;
    }

    // ── CONNECT: validate JWT and set Principal ──────────────────────
    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                if (jwtUtil.validateToken(token) && !jwtUtil.isTokenExpired(token)) {
                    String email = jwtUtil.extractEmail(token);
                    String role = jwtUtil.extractRole(token);

                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);
                    accessor.setUser(authentication);
                    log.debug("WebSocket STOMP CONNECT authenticated for user: {}", email);
                } else {
                    log.warn("WebSocket STOMP CONNECT with invalid/expired JWT");
                }
            } catch (Exception e) {
                log.warn("WebSocket JWT authentication failed: {}", e.getMessage());
            }
        } else {
            log.debug("WebSocket STOMP CONNECT without Authorization header");
        }
    }

    // ── SUBSCRIBE: enforce topic-level authorization ─────────────────
    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Principal principal = accessor.getUser();

        // Reject if not authenticated or no destination
        if (destination == null || principal == null) {
            log.warn("WebSocket SUBSCRIBE rejected: missing destination or authentication");
            throw new MessageDeliveryException("Unauthorized subscription");
        }

        String email = principal.getName();

        // ── Trip topics: require APPROVED membership ──
        Matcher tripMatcher = TRIP_TOPIC_PATTERN.matcher(destination);
        if (tripMatcher.matches()) {
            validateTripSubscription(email, tripMatcher.group(1), destination);
            return;
        }

        // ── User topics: require matching user ID ──
        Matcher userMatcher = USER_TOPIC_PATTERN.matcher(destination);
        if (userMatcher.matches()) {
            validateUserSubscription(email, userMatcher.group(1), destination);
            return;
        }

        // Unknown topic pattern — reject by default
        log.warn("WebSocket SUBSCRIBE rejected: unrecognized topic pattern {}", destination);
        throw new MessageDeliveryException("Unauthorized subscription: unknown topic");
    }

    private void validateTripSubscription(String email, String tripIdStr, String destination) {
        UUID tripId;
        try {
            tripId = UUID.fromString(tripIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("WebSocket SUBSCRIBE rejected: invalid tripId in destination {}", destination);
            throw new MessageDeliveryException("Invalid subscription destination");
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("WebSocket SUBSCRIBE rejected: user not found for email {}", email);
            throw new MessageDeliveryException("Unauthorized subscription");
        }

        boolean isMember = tripMemberRepository.findByTripIdAndUserId(tripId, user.getId())
                .filter(m -> m.getMemberStatus() == MemberStatus.APPROVED)
                .isPresent();

        if (!isMember) {
            log.warn("WebSocket SUBSCRIBE rejected: user {} is not an approved member of trip {}", email, tripId);
            throw new MessageDeliveryException("Unauthorized subscription: not a trip member");
        }

        log.debug("WebSocket SUBSCRIBE authorized: {} → {}", email, destination);
    }

    private void validateUserSubscription(String email, String topicUserId, String destination) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.getId().toString().equals(topicUserId)) {
            log.warn("WebSocket SUBSCRIBE rejected: user {} tried to subscribe to another user's topic {}", email, destination);
            throw new MessageDeliveryException("Unauthorized subscription: cannot access another user's topics");
        }

        log.debug("WebSocket SUBSCRIBE authorized: {} → {}", email, destination);
    }
}
