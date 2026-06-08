package com.travyn.trip.controller;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.exception.UserNotFoundException;
import com.travyn.trip.dto.TripWaypointDTO;
import com.travyn.trip.service.TripMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/map")
@RequiredArgsConstructor
public class MapRestController {

    private final TripMapService mapService;
    private final UserRepository userRepository;

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    @GetMapping("/waypoints")
    public ResponseEntity<List<TripWaypointDTO>> getWaypoints(
            @PathVariable UUID tripId,
            @AuthenticationPrincipal String email) {
        User user = findUserByEmail(email);
        return ResponseEntity.ok(mapService.getWaypoints(tripId, user.getId()));
    }

    public static class AddWaypointRequest {
        public Double latitude;
        public Double longitude;
        public String label;
    }

    @PostMapping("/waypoints")
    public ResponseEntity<TripWaypointDTO> addWaypoint(
            @PathVariable UUID tripId,
            @RequestBody AddWaypointRequest request,
            @AuthenticationPrincipal String email) {
        User user = findUserByEmail(email);
        return ResponseEntity.ok(mapService.addWaypoint(tripId, user.getId(), request.latitude, request.longitude, request.label));
    }

    @DeleteMapping("/waypoints/{waypointId}")
    public ResponseEntity<Void> deleteWaypoint(
            @PathVariable UUID tripId,
            @PathVariable UUID waypointId,
            @AuthenticationPrincipal String email) {
        User user = findUserByEmail(email);
        mapService.deleteWaypoint(tripId, user.getId(), waypointId);
        return ResponseEntity.ok().build();
    }
}
