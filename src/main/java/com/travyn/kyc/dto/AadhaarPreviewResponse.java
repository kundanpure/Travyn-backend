package com.travyn.kyc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AadhaarPreviewResponse {
    private String extractedName;   // Full name from Aadhaar
    private String firstName;       // Derived: all words except last
    private String lastName;        // Derived: last word
    private String gender;          // MALE / FEMALE / NON_BINARY
    private String dob;             // YYYY-MM-DD
    private String aadhaarLast4;    // Last 4 digits for display
    private String previewToken;    // 15-min HMAC-signed token, used in /auth/register
}
