package com.travyn.trip.service;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.expense.exception.ExpenseAccessDeniedException;
import com.travyn.trip.dto.TripWaypointDTO;
import com.travyn.trip.entity.MemberStatus;
import com.travyn.trip.entity.Trip;
import com.travyn.trip.entity.TripWaypoint;
import com.travyn.trip.exception.TripNotFoundException;
import com.travyn.trip.repository.TripMemberRepository;
import com.travyn.trip.repository.TripRepository;
import com.travyn.trip.repository.TripWaypointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripMapService {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripWaypointRepository waypointRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public List<TripWaypointDTO> getWaypoints(UUID tripId, UUID userId) {
        validateMembership(userId, tripId);
        List<TripWaypoint> waypoints = waypointRepository.findByTripIdOrderByCreatedAtAsc(tripId);
        
        List<UUID> creatorIds = waypoints.stream().map(TripWaypoint::getCreatorId).collect(Collectors.toList());
        Map<UUID, User> users = userRepository.findAllById(creatorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return waypoints.stream().map(wp -> mapToDTO(wp, users.get(wp.getCreatorId()))).toList();
    }

    @Transactional
    public TripWaypointDTO addWaypoint(UUID tripId, UUID userId, Double lat, Double lng, String label) {
        validateMembership(userId, tripId);
        
        TripWaypoint waypoint = TripWaypoint.builder()
                .tripId(tripId)
                .creatorId(userId)
                .latitude(lat)
                .longitude(lng)
                .label(label)
                .build();
                
        waypoint = waypointRepository.save(waypoint);
        User creator = userRepository.findById(userId).orElse(null);
        TripWaypointDTO dto = mapToDTO(waypoint, creator);

        // Broadcast new waypoint to subscribers
        messagingTemplate.convertAndSend("/topic/map/" + tripId.toString() + "/waypoints", dto);
        
        return dto;
    }

    @Transactional
    public void deleteWaypoint(UUID tripId, UUID userId, UUID waypointId) {
        validateMembership(userId, tripId);
        TripWaypoint wp = waypointRepository.findById(waypointId)
                .orElseThrow(() -> new IllegalArgumentException("Waypoint not found"));
                
        Trip trip = tripRepository.findById(tripId).orElseThrow();
        if (!wp.getCreatorId().equals(userId) && !trip.getCreatorId().equals(userId)) {
            throw new ExpenseAccessDeniedException("Cannot delete someone else's waypoint");
        }
        
        waypointRepository.delete(wp);
        messagingTemplate.convertAndSend("/topic/map/" + tripId.toString() + "/waypoints/delete", waypointId.toString());
    }

    private TripWaypointDTO mapToDTO(TripWaypoint wp, User creator) {
        String name = "Unknown";
        String initials = "??";
        if (creator != null) {
            name = creator.getFirstName() + " " + creator.getLastName();
            initials = ("" + creator.getFirstName().charAt(0) + creator.getLastName().charAt(0)).toUpperCase();
        }
        return TripWaypointDTO.builder()
                .id(wp.getId())
                .tripId(wp.getTripId())
                .latitude(wp.getLatitude())
                .longitude(wp.getLongitude())
                .label(wp.getLabel())
                .creatorId(wp.getCreatorId())
                .creatorName(name)
                .creatorInitials(initials)
                .createdAt(wp.getCreatedAt())
                .build();
    }

    private void validateMembership(UUID userId, UUID tripId) {
        boolean isMember = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .filter(m -> m.getMemberStatus() == MemberStatus.APPROVED)
                .isPresent();

        if (!isMember) {
            throw new ExpenseAccessDeniedException("You must be an approved member of this trip to view the map");
        }
    }
}
