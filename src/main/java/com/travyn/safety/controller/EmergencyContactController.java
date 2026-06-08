package com.travyn.safety.controller;

import com.travyn.safety.dto.CreateEmergencyContactRequest;
import com.travyn.safety.dto.EmergencyContactDTO;
import com.travyn.safety.service.EmergencyContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/emergency-contacts")
@RequiredArgsConstructor
@Tag(name = "Emergency Contacts", description = "Manage emergency contacts for safety features")
public class EmergencyContactController {

    private final EmergencyContactService contactService;
    private final com.travyn.auth.repository.UserRepository userRepository;

    private UUID getUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
    }

    @GetMapping
    @Operation(summary = "Get my emergency contacts")
    public ResponseEntity<List<EmergencyContactDTO>> getMyContacts(@AuthenticationPrincipal String email) {
        UUID userId = getUserId(email);
        return ResponseEntity.ok(contactService.getMyContacts(userId));
    }

    @PostMapping
    @Operation(summary = "Add an emergency contact")
    public ResponseEntity<EmergencyContactDTO> addContact(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody CreateEmergencyContactRequest request) {
        UUID userId = getUserId(email);
        EmergencyContactDTO contact = contactService.addContact(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(contact);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an emergency contact")
    public ResponseEntity<Void> deleteContact(
            @AuthenticationPrincipal String email,
            @PathVariable UUID id) {
        UUID userId = getUserId(email);
        contactService.deleteContact(userId, id);
        return ResponseEntity.noContent().build();
    }
}
