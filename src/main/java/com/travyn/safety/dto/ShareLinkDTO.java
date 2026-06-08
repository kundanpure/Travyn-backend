package com.travyn.safety.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareLinkDTO {
    private String contactName;
    private String contactEmail;
    private String shareUrl;
    private String expiresAt;
}
