package com.travyn.trip.controller;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.exception.UserNotFoundException;
import com.travyn.trip.dto.*;
import com.travyn.trip.entity.TripType;
import com.travyn.trip.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
@Tag(name = "Trips", description = "Trip management and discovery")
public class TripController {

    private final TripService tripService;
    private final UserRepository userRepository;

    @PostMapping
    @Operation(summary = "Create a new trip")
    public ResponseEntity<TripDTO> createTrip(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody CreateTripRequest request) {
        User user = findUserByEmail(email);
        TripDTO trip = tripService.createTrip(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(trip);
    }

    @GetMapping
    @Operation(summary = "Discover public trips with optional filters")
    public ResponseEntity<Page<TripCardDTO>> discoverTrips(
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) TripType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TripCardDTO> trips = tripService.discoverTrips(destination, type, fromDate, toDate, page, size);
        return ResponseEntity.ok(trips);
    }

    @GetMapping("/my-trips")
    @Operation(summary = "Get current user's trips (created and joined)")
    public ResponseEntity<List<TripDTO>> getMyTrips(@AuthenticationPrincipal String email) {
        User user = findUserByEmail(email);
        List<TripDTO> trips = tripService.getMyTrips(user.getId());
        return ResponseEntity.ok(trips);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get trip details by ID")
    public ResponseEntity<TripDTO> getTrip(@PathVariable UUID id) {
        TripDTO trip = tripService.getTrip(id);
        return ResponseEntity.ok(trip);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a trip (creator only)")
    public ResponseEntity<TripDTO> updateTrip(
            @AuthenticationPrincipal String email,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTripRequest request) {
        User user = findUserByEmail(email);
        TripDTO trip = tripService.updateTrip(user.getId(), id, request);
        return ResponseEntity.ok(trip);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a trip (creator only)")
    public ResponseEntity<Void> cancelTrip(
            @AuthenticationPrincipal String email,
            @PathVariable UUID id) {
        User user = findUserByEmail(email);
        tripService.cancelTrip(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/join")
    @Operation(summary = "Request to join a trip")
    public ResponseEntity<TripMemberDTO> requestJoin(
            @AuthenticationPrincipal String email,
            @PathVariable UUID id) {
        User user = findUserByEmail(email);
        TripMemberDTO member = tripService.requestJoin(user.getId(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "List trip members")
    public ResponseEntity<List<TripMemberDTO>> getTripMembers(@PathVariable UUID id) {
        List<TripMemberDTO> members = tripService.getTripMembers(id);
        return ResponseEntity.ok(members);
    }

    @GetMapping("/{id}/requests")
    @Operation(summary = "Get pending join requests (creator only)")
    public ResponseEntity<List<JoinRequestDTO>> getPendingRequests(
            @AuthenticationPrincipal String email,
            @PathVariable UUID id) {
        // Access control is handled at service level via trip creator check
        List<JoinRequestDTO> requests = tripService.getPendingRequests(id);
        return ResponseEntity.ok(requests);
    }

    @PutMapping("/{id}/requests/{memberId}")
    @Operation(summary = "Approve or reject a join request (creator only)")
    public ResponseEntity<TripMemberDTO> handleJoinRequest(
            @AuthenticationPrincipal String email,
            @PathVariable UUID id,
            @PathVariable UUID memberId,
            @Valid @RequestBody HandleJoinRequest request) {
        User user = findUserByEmail(email);
        TripMemberDTO member = tripService.handleJoinRequest(user.getId(), id, memberId, request.getStatus());
        return ResponseEntity.ok(member);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
