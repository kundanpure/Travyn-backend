package com.travyn.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetConfirm {

    @NotBlank(message = "Please provide a valid token")
    private String token;

    @NotBlank(message = "Please provide a valid password")
    @Size(min = 10, max = 128, message = "Password must be between 10 and 128 characters")
    private String newPassword;
}
