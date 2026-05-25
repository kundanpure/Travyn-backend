package com.travyn.itinerary.service;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.exception.UserNotFoundException;
import com.travyn.itinerary.dto.*;
import com.travyn.itinerary.entity.ItineraryDay;
import com.travyn.itinerary.entity.ItineraryItem;
import com.travyn.itinerary.entity.ItemCategory;
import com.travyn.itinerary.exception.ItineraryAccessDeniedException;
import com.travyn.itinerary.exception.ItineraryNotFoundException;
import com.travyn.itinerary.repository.ItineraryDayRepository;
import com.travyn.itinerary.repository.ItineraryItemRepository;
import com.travyn.trip.entity.MemberStatus;
import com.travyn.trip.entity.Trip;
import com.travyn.trip.exception.TripNotFoundException;
import com.travyn.trip.repository.TripMemberRepository;
import com.travyn.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItineraryService {

    private final ItineraryDayRepository dayRepository;
    private final ItineraryItemRepository itemRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;

    /**
     * Get the full itinerary for a trip.
     * Auto-scaffolds day entries for each date in the trip range if none exist yet.
     */
    @Transactional
    public List<ItineraryDayDTO> getItinerary(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        List<ItineraryDay> days = dayRepository.findByTripIdOrderByDateAsc(tripId);

        // Auto-scaffold: if no days exist, create one for each date in the trip range
        if (days.isEmpty()) {
            days = scaffoldDays(trip);
        }

        // Build a map of items grouped by dayId
        List<ItineraryItem> allItems = itemRepository.findByTripId(tripId);
        Map<UUID, List<ItineraryItem>> itemsByDay = allItems.stream()
                .collect(Collectors.groupingBy(ItineraryItem::getDayId));

        // Collect all unique user IDs from items for name resolution
        Set<UUID> userIds = allItems.stream().map(ItineraryItem::getCreatedBy).collect(Collectors.toSet());
        Map<UUID, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        LocalDate tripStart = trip.getStartDate();

        return days.stream().map(day -> {
            List<ItineraryItem> dayItems = itemsByDay.getOrDefault(day.getId(), new ArrayList<>());
            dayItems.sort(Comparator.comparingInt(ItineraryItem::getSortOrder));

            int dayNumber = (int) ChronoUnit.DAYS.between(tripStart, day.getDate()) + 1;

            List<ItineraryDayDTO.ItineraryItemDTO> itemDTOs = dayItems.stream()
                    .map(item -> {
                        User creator = usersById.get(item.getCreatedBy());
                        return ItineraryDayDTO.ItineraryItemDTO.builder()
                                .id(item.getId())
                                .dayId(item.getDayId())
                                .title(item.getTitle())
                                .description(item.getDescription())
                                .location(item.getLocation())
                                .startTime(item.getStartTime())
                                .endTime(item.getEndTime())
                                .category(item.getCategory())
                                .sortOrder(item.getSortOrder())
                                .createdBy(item.getCreatedBy())
                                .createdByName(creator != null ? creator.getFirstName() + " " + creator.getLastName() : null)
                                .createdAt(item.getCreatedAt())
                                .build();
                    }).toList();

            return ItineraryDayDTO.builder()
                    .id(day.getId())
                    .tripId(day.getTripId())
                    .date(day.getDate())
                    .title(day.getTitle())
                    .notes(day.getNotes())
                    .dayNumber(dayNumber)
                    .items(itemDTOs)
                    .createdAt(day.getCreatedAt())
                    .updatedAt(day.getUpdatedAt())
                    .build();
        }).toList();
    }

    /**
     * Auto-create ItineraryDay records for every date from trip.startDate to trip.endDate.
     */
    private List<ItineraryDay> scaffoldDays(Trip trip) {
        List<ItineraryDay> days = new ArrayList<>();
        LocalDate current = trip.getStartDate();
        int dayNum = 1;

        while (!current.isAfter(trip.getEndDate())) {
            ItineraryDay day = ItineraryDay.builder()
                    .tripId(trip.getId())
                    .date(current)
                    .title("Day " + dayNum + " — " + current.toString())
                    .build();
            days.add(day);
            current = current.plusDays(1);
            dayNum++;
        }

        days = dayRepository.saveAll(days);
        log.info("Auto-scaffolded {} itinerary days for trip {}", days.size(), trip.getTripCode());
        return days;
    }

    @Transactional
    public ItineraryDayDTO createDay(UUID userId, UUID tripId, CreateDayRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));
        validateMembership(userId, tripId);

        ItineraryDay day = ItineraryDay.builder()
                .tripId(tripId)
                .date(request.getDate())
                .title(request.getTitle())
                .notes(request.getNotes())
                .build();

        day = dayRepository.save(day);
        log.info("Itinerary day created for trip {} on date {} by user {}", trip.getTripCode(), request.getDate(), userId);

        int dayNumber = (int) ChronoUnit.DAYS.between(trip.getStartDate(), day.getDate()) + 1;

        return ItineraryDayDTO.builder()
                .id(day.getId())
                .tripId(day.getTripId())
                .date(day.getDate())
                .title(day.getTitle())
                .notes(day.getNotes())
                .dayNumber(dayNumber)
                .items(List.of())
                .createdAt(day.getCreatedAt())
                .updatedAt(day.getUpdatedAt())
                .build();
    }

    @Transactional
    public ItineraryDayDTO updateDay(UUID userId, UUID tripId, UUID dayId, UpdateDayRequest request) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));
        validateMembership(userId, tripId);

        ItineraryDay day = dayRepository.findById(dayId)
                .orElseThrow(() -> new ItineraryNotFoundException("Itinerary day not found"));

        if (!day.getTripId().equals(tripId)) {
            throw new ItineraryAccessDeniedException("Day does not belong to this trip");
        }

        if (request.getTitle() != null) {
            day.setTitle(request.getTitle());
        }
        if (request.getNotes() != null) {
            day.setNotes(request.getNotes());
        }

        day = dayRepository.save(day);
        return mapDayToDTO(day);
    }

    @Transactional
    public void deleteDay(UUID userId, UUID tripId, UUID dayId) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));
        validateMembership(userId, tripId);

        ItineraryDay day = dayRepository.findById(dayId)
                .orElseThrow(() -> new ItineraryNotFoundException("Itinerary day not found"));

        if (!day.getTripId().equals(tripId)) {
            throw new ItineraryAccessDeniedException("Day does not belong to this trip");
        }

        // Items are cascade-deleted via FK
        dayRepository.delete(day);
        log.info("Itinerary day {} deleted from trip {} by user {}", dayId, tripId, userId);
    }

    @Transactional
    public ItineraryDayDTO.ItineraryItemDTO addItem(UUID userId, UUID tripId, UUID dayId, CreateItemRequest request) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));
        validateMembership(userId, tripId);

        ItineraryDay day = dayRepository.findById(dayId)
                .orElseThrow(() -> new ItineraryNotFoundException("Itinerary day not found"));

        if (!day.getTripId().equals(tripId)) {
            throw new ItineraryAccessDeniedException("Day does not belong to this trip");
        }

        int nextOrder = itemRepository.countByDayId(dayId);

        ItineraryItem item = ItineraryItem.builder()
                .dayId(dayId)
                .tripId(tripId)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .location(request.getLocation())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .category(request.getCategory() != null ? request.getCategory() : ItemCategory.ACTIVITY)
                .sortOrder(nextOrder)
                .createdBy(userId)
                .build();

        item = itemRepository.save(item);

        User creator = userRepository.findById(userId).orElse(null);

        return ItineraryDayDTO.ItineraryItemDTO.builder()
                .id(item.getId())
                .dayId(item.getDayId())
                .title(item.getTitle())
                .description(item.getDescription())
                .location(item.getLocation())
                .startTime(item.getStartTime())
                .endTime(item.getEndTime())
                .category(item.getCategory())
                .sortOrder(item.getSortOrder())
                .createdBy(item.getCreatedBy())
                .createdByName(creator != null ? creator.getFirstName() + " " + creator.getLastName() : null)
                .createdAt(item.getCreatedAt())
                .build();
    }

    @Transactional
    public ItineraryDayDTO.ItineraryItemDTO updateItem(UUID userId, UUID tripId, UUID itemId, UpdateItemRequest request) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));
        validateMembership(userId, tripId);

        ItineraryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItineraryNotFoundException("Itinerary item not found"));

        if (!item.getTripId().equals(tripId)) {
            throw new ItineraryAccessDeniedException("Item does not belong to this trip");
        }

        if (request.getTitle() != null) item.setTitle(request.getTitle().trim());
        if (request.getDescription() != null) item.setDescription(request.getDescription());
        if (request.getLocation() != null) item.setLocation(request.getLocation());
        if (request.getStartTime() != null) item.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) item.setEndTime(request.getEndTime());
        if (request.getCategory() != null) item.setCategory(request.getCategory());

        item = itemRepository.save(item);

        User creator = userRepository.findById(item.getCreatedBy()).orElse(null);

        return ItineraryDayDTO.ItineraryItemDTO.builder()
                .id(item.getId())
                .dayId(item.getDayId())
                .title(item.getTitle())
                .description(item.getDescription())
                .location(item.getLocation())
                .startTime(item.getStartTime())
                .endTime(item.getEndTime())
                .category(item.getCategory())
                .sortOrder(item.getSortOrder())
                .createdBy(item.getCreatedBy())
                .createdByName(creator != null ? creator.getFirstName() + " " + creator.getLastName() : null)
                .createdAt(item.getCreatedAt())
                .build();
    }

    @Transactional
    public void deleteItem(UUID userId, UUID tripId, UUID itemId) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));
        validateMembership(userId, tripId);

        ItineraryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItineraryNotFoundException("Itinerary item not found"));

        if (!item.getTripId().equals(tripId)) {
            throw new ItineraryAccessDeniedException("Item does not belong to this trip");
        }

        itemRepository.delete(item);
        log.info("Itinerary item {} deleted from trip {} by user {}", itemId, tripId, userId);
    }

    @Transactional
    public void reorderItems(UUID userId, UUID dayId, ReorderItemsRequest request) {
        ItineraryDay day = dayRepository.findById(dayId)
                .orElseThrow(() -> new ItineraryNotFoundException("Itinerary day not found"));
        validateMembership(userId, day.getTripId());

        List<ItineraryItem> items = itemRepository.findByDayIdOrderBySortOrderAsc(dayId);
        Map<UUID, ItineraryItem> itemMap = items.stream()
                .collect(Collectors.toMap(ItineraryItem::getId, i -> i));

        List<ItineraryItem> toSave = new ArrayList<>();
        for (int i = 0; i < request.getItemIds().size(); i++) {
            ItineraryItem item = itemMap.get(request.getItemIds().get(i));
            if (item != null) {
                item.setSortOrder(i);
                toSave.add(item);
            }
        }

        itemRepository.saveAll(toSave);
        log.info("Reordered {} items in day {} by user {}", toSave.size(), dayId, userId);
    }

    private void validateMembership(UUID userId, UUID tripId) {
        boolean isMember = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .filter(m -> m.getMemberStatus() == MemberStatus.APPROVED)
                .isPresent();

        if (!isMember) {
            throw new ItineraryAccessDeniedException("You must be an approved member of this trip");
        }
    }

    private ItineraryDayDTO mapDayToDTO(ItineraryDay day) {
        Trip trip = tripRepository.findById(day.getTripId()).orElse(null);
        int dayNumber = trip != null
                ? (int) ChronoUnit.DAYS.between(trip.getStartDate(), day.getDate()) + 1
                : 0;

        List<ItineraryItem> items = itemRepository.findByDayIdOrderBySortOrderAsc(day.getId());

        Set<UUID> userIds = items.stream().map(ItineraryItem::getCreatedBy).collect(Collectors.toSet());
        Map<UUID, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ItineraryDayDTO.ItineraryItemDTO> itemDTOs = items.stream().map(item -> {
            User creator = usersById.get(item.getCreatedBy());
            return ItineraryDayDTO.ItineraryItemDTO.builder()
                    .id(item.getId())
                    .dayId(item.getDayId())
                    .title(item.getTitle())
                    .description(item.getDescription())
                    .location(item.getLocation())
                    .startTime(item.getStartTime())
                    .endTime(item.getEndTime())
                    .category(item.getCategory())
                    .sortOrder(item.getSortOrder())
                    .createdBy(item.getCreatedBy())
                    .createdByName(creator != null ? creator.getFirstName() + " " + creator.getLastName() : null)
                    .createdAt(item.getCreatedAt())
                    .build();
        }).toList();

        return ItineraryDayDTO.builder()
                .id(day.getId())
                .tripId(day.getTripId())
                .date(day.getDate())
                .title(day.getTitle())
                .notes(day.getNotes())
                .dayNumber(dayNumber)
                .items(itemDTOs)
                .createdAt(day.getCreatedAt())
                .updatedAt(day.getUpdatedAt())
                .build();
    }
}
