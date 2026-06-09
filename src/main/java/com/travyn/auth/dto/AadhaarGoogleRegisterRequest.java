package com.travyn.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AadhaarGoogleRegisterRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Aadhaar preview token is required")
    private String previewToken;

    @NotBlank(message = "Google credential token is required")
    private String credential;
}
