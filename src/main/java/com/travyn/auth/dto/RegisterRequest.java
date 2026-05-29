package com.travyn.auth.dto;

import com.travyn.auth.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    /**
     * Required for email-first registration.
     * Optional when previewToken is provided (name is taken from Aadhaar).
     */
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    private String firstName;

    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    private String lastName;

    @NotBlank(message = "Please provide a valid username")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    @jakarta.validation.constraints.Pattern(regexp = "^[a-z0-9_.]+$", message = "Username can only contain lowercase letters, numbers, underscores, and periods")
    private String username;

    @NotBlank(message = "Please provide a valid email")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Please provide a valid password")
    @Size(min = 10, max = 128, message = "Password must be between 10 and 128 characters")
    private String password;

    /**
     * Required for email-first registration.
     * Optional when previewToken is provided (gender is taken from Aadhaar).
     */
    private Gender gender;

    /**
     * Aadhaar-first registration path.
     * When provided: firstName, lastName, gender are populated from the token.
     * When null: firstName, lastName, gender must be explicitly provided.
     */
    private String previewToken;
}
