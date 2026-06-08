package com.travyn.safety.controller;

import com.travyn.safety.dto.PublicLocationDTO;
import com.travyn.safety.service.LocationSharingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/location")
@RequiredArgsConstructor
@Tag(name = "Public Location Tracking", description = "Public endpoint for tracking via token")
public class PublicLocationController {

    private final LocationSharingService sharingService;

    @GetMapping("/{token}")
    @Operation(summary = "Get location for a tracking token")
    public ResponseEntity<PublicLocationDTO> getPublicLocation(@PathVariable String token) {
        return ResponseEntity.ok(sharingService.getPublicLocation(token));
    }
}
