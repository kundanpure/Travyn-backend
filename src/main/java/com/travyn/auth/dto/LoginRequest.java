package com.travyn.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Please provide a valid email")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Please provide a valid password")
    private String password;
}
