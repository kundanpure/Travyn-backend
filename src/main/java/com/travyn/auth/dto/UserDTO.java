package com.travyn.auth.dto;

import com.travyn.auth.entity.Gender;
import com.travyn.auth.entity.Role;
import com.travyn.auth.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private UserStatus status;
    private boolean emailVerified;
    private Gender gender;
    /** How many more times the user can change their gender (max 2 total). */
    private int genderChangesRemaining;
    private Instant createdAt;
}

