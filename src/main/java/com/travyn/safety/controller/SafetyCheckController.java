package com.travyn.safety.controller;

import com.travyn.auth.entity.User;
import com.travyn.common.exception.ResourceNotFoundException;
import com.travyn.safety.dto.SafetyCheckDTO;
import com.travyn.safety.entity.SafetyCheck;
import com.travyn.safety.entity.SafetyCheckStatus;
import com.travyn.safety.repository.SafetyCheckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.exception.UserNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/safety-checks")
@RequiredArgsConstructor
public class SafetyCheckController {

    private final SafetyCheckRepository safetyCheckRepository;
    private final UserRepository userRepository;

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    @GetMapping("/active")
    public ResponseEntity<List<SafetyCheckDTO>> getActiveChecks(@AuthenticationPrincipal String email) {
        User user = findUserByEmail(email);
        List<SafetyCheck> checks = safetyCheckRepository.findByUserIdAndStatus(user.getId(), SafetyCheckStatus.PENDING);
        
        List<SafetyCheckDTO> dtos = checks.stream()
                .filter(c -> c.getExpiresAt().isAfter(Instant.now()))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Void> resolveCheck(
            @PathVariable UUID id,
            @AuthenticationPrincipal String email) {
        User user = findUserByEmail(email);
        
        SafetyCheck check = safetyCheckRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Safety check not found"));
                
        if (!check.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Not authorized to resolve this safety check");
        }
        
        if (check.getStatus() == SafetyCheckStatus.PENDING) {
            check.setStatus(SafetyCheckStatus.RESOLVED);
            check.setResolvedAt(Instant.now());
            safetyCheckRepository.save(check);
        }
        
        return ResponseEntity.ok().build();
    }

    private SafetyCheckDTO mapToDTO(SafetyCheck check) {
        return SafetyCheckDTO.builder()
                .id(check.getId())
                .tripId(check.getTripId())
                .status(check.getStatus())
                .createdAt(check.getCreatedAt())
                .expiresAt(check.getExpiresAt())
                .build();
    }
}
