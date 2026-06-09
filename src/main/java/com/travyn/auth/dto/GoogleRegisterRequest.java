package com.travyn.auth.dto;

import com.travyn.auth.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleRegisterRequest {
    
    @NotBlank(message = "Google credential token is required")
    private String credential;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    private String lastName;

    @NotBlank(message = "Please provide a valid username")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    @jakarta.validation.constraints.Pattern(regexp = "^[a-z0-9_.]+$", message = "Username can only contain lowercase letters, numbers, underscores, and periods")
    private String username;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotNull(message = "Date of birth is required")
    private LocalDate dateOfBirth;
}
