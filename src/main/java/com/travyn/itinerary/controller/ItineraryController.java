package com.travyn.itinerary.controller;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.exception.UserNotFoundException;
import com.travyn.itinerary.dto.*;
import com.travyn.itinerary.service.ItineraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/itinerary")
@RequiredArgsConstructor
@Tag(name = "Itinerary", description = "Trip itinerary planning")
public class ItineraryController {

    private final ItineraryService itineraryService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Get full itinerary for a trip (auto-scaffolds days if none exist)")
    public ResponseEntity<List<ItineraryDayDTO>> getItinerary(@PathVariable UUID tripId) {
        List<ItineraryDayDTO> days = itineraryService.getItinerary(tripId);
        return ResponseEntity.ok(days);
    }

    @PostMapping("/days")
    @Operation(summary = "Add a new day to the itinerary")
    public ResponseEntity<ItineraryDayDTO> createDay(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateDayRequest request) {
        User user = findUserByEmail(email);
        ItineraryDayDTO day = itineraryService.createDay(user.getId(), tripId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(day);
    }

    @PutMapping("/days/{dayId}")
    @Operation(summary = "Update a day's title or notes")
    public ResponseEntity<ItineraryDayDTO> updateDay(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId,
            @PathVariable UUID dayId,
            @Valid @RequestBody UpdateDayRequest request) {
        User user = findUserByEmail(email);
        ItineraryDayDTO day = itineraryService.updateDay(user.getId(), tripId, dayId, request);
        return ResponseEntity.ok(day);
    }

    @DeleteMapping("/days/{dayId}")
    @Operation(summary = "Delete a day and all its items")
    public ResponseEntity<Void> deleteDay(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId,
            @PathVariable UUID dayId) {
        User user = findUserByEmail(email);
        itineraryService.deleteDay(user.getId(), tripId, dayId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/days/{dayId}/items")
    @Operation(summary = "Add an activity item to a day")
    public ResponseEntity<ItineraryDayDTO.ItineraryItemDTO> addItem(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId,
            @PathVariable UUID dayId,
            @Valid @RequestBody CreateItemRequest request) {
        User user = findUserByEmail(email);
        ItineraryDayDTO.ItineraryItemDTO item = itineraryService.addItem(user.getId(), tripId, dayId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Update an activity item")
    public ResponseEntity<ItineraryDayDTO.ItineraryItemDTO> updateItem(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateItemRequest request) {
        User user = findUserByEmail(email);
        ItineraryDayDTO.ItineraryItemDTO item = itineraryService.updateItem(user.getId(), tripId, itemId, request);
        return ResponseEntity.ok(item);
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Delete an activity item")
    public ResponseEntity<Void> deleteItem(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId,
            @PathVariable UUID itemId) {
        User user = findUserByEmail(email);
        itineraryService.deleteItem(user.getId(), tripId, itemId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/days/{dayId}/reorder")
    @Operation(summary = "Reorder items within a day")
    public ResponseEntity<Void> reorderItems(
            @AuthenticationPrincipal String email,
            @PathVariable UUID tripId,
            @PathVariable UUID dayId,
            @Valid @RequestBody ReorderItemsRequest request) {
        User user = findUserByEmail(email);
        itineraryService.reorderItems(user.getId(), dayId, request);
        return ResponseEntity.ok().build();
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
