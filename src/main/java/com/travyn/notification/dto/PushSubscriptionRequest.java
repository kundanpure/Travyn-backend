package com.travyn.notification.dto;

import lombok.Data;

@Data
public class PushSubscriptionRequest {
    private String endpoint;
    private String p256dh;
    private String auth;
}
