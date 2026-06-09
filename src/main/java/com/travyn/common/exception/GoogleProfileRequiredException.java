package com.travyn.common.exception;

import lombok.Getter;

@Getter
public class GoogleProfileRequiredException extends RuntimeException {
    
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String profilePictureUrl;

    public GoogleProfileRequiredException(String email, String firstName, String lastName, String profilePictureUrl) {
        super("Profile completion required");
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.profilePictureUrl = profilePictureUrl;
    }
}
