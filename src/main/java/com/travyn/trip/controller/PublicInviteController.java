package com.travyn.trip.controller;

import com.travyn.trip.dto.TripInvitePreviewDTO;
import com.travyn.trip.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public Invite", description = "Public trip invite preview (no auth required)")
public class PublicInviteController {

    private final TripService tripService;

    @GetMapping("/invite/{token}")
    @Operation(summary = "Preview a trip invite link (no authentication required)")
    public ResponseEntity<TripInvitePreviewDTO> previewInvite(@PathVariable String token) {
        TripInvitePreviewDTO preview = tripService.previewInvite(token);
        return ResponseEntity.ok(preview);
    }
}
