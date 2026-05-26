package com.travyn.auth.dto;

import com.travyn.auth.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotBlank(message = "Please provide a valid first name")
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    private String firstName;

    @NotBlank(message = "Please provide a valid last name")
    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    private String lastName;

    @NotBlank(message = "Please provide a valid email")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Please provide a valid password")
    @Size(min = 10, max = 128, message = "Password must be between 10 and 128 characters")
    private String password;

    @NotNull(message = "Please select your gender")
    private Gender gender;
}
