package com.travyn.trip.service;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.exception.UserNotFoundException;
import com.travyn.trip.dto.*;
import com.travyn.trip.entity.*;
import com.travyn.trip.exception.AlreadyMemberException;
import com.travyn.trip.exception.TripAccessDeniedException;
import com.travyn.trip.exception.TripFullException;
import com.travyn.trip.exception.TripNotFoundException;
import com.travyn.trip.repository.TripMemberRepository;
import com.travyn.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripService {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;

    private static final String TRIP_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TRIP_CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public TripDTO createTrip(UUID userId, CreateTripRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Trip trip = Trip.builder()
                .creatorId(userId)
                .title(request.getTitle().trim())
                .destination(request.getDestination().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .maxSize(request.getMaxSize())
                .tripType(request.getTripType())
                .status(TripStatus.OPEN)
                .trustScoreMin(request.getTrustScoreMin())
                .womenOnly(request.isWomenOnly())
                .approvalMode(request.getApprovalMode() != null ? request.getApprovalMode() : ApprovalMode.MANUAL)
                .tags(request.getTags())
                .tripCode(generateTripCode())
                .coverImageUrl(request.getCoverImageUrl())
                .build();

        trip = tripRepository.save(trip);

        // Auto-add creator as an APPROVED CREATOR member
        TripMember creatorMember = TripMember.builder()
                .tripId(trip.getId())
                .userId(userId)
                .memberRole(MemberRole.CREATOR)
                .memberStatus(MemberStatus.APPROVED)
                .build();
        tripMemberRepository.save(creatorMember);

        log.info("Trip created: {} by user: {}", trip.getTripCode(), userId);

        return mapToTripDTO(trip, creator);
    }

    @Transactional(readOnly = true)
    public TripDTO getTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        User creator = userRepository.findById(trip.getCreatorId())
                .orElseThrow(() -> new UserNotFoundException("Trip creator not found"));

        return mapToTripDTO(trip, creator);
    }

    @Transactional
    public TripDTO updateTrip(UUID userId, UUID tripId, UpdateTripRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (!trip.getCreatorId().equals(userId)) {
            throw new TripAccessDeniedException("Only the trip creator can update this trip");
        }

        if (request.getTitle() != null) {
            trip.setTitle(request.getTitle().trim());
        }
        if (request.getDestination() != null) {
            trip.setDestination(request.getDestination().trim());
        }
        if (request.getDescription() != null) {
            trip.setDescription(request.getDescription().trim());
        }
        if (request.getStartDate() != null) {
            trip.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            trip.setEndDate(request.getEndDate());
        }
        if (request.getMaxSize() != null) {
            trip.setMaxSize(request.getMaxSize());
        }
        if (request.getTripType() != null) {
            trip.setTripType(request.getTripType());
        }
        if (request.getTrustScoreMin() != null) {
            trip.setTrustScoreMin(request.getTrustScoreMin());
        }
        if (request.getWomenOnly() != null) {
            trip.setWomenOnly(request.getWomenOnly());
        }
        if (request.getApprovalMode() != null) {
            trip.setApprovalMode(request.getApprovalMode());
        }
        if (request.getTags() != null) {
            trip.setTags(request.getTags());
        }
        if (request.getCoverImageUrl() != null) {
            trip.setCoverImageUrl(request.getCoverImageUrl());
        }

        trip = tripRepository.save(trip);

        User creator = userRepository.findById(trip.getCreatorId())
                .orElseThrow(() -> new UserNotFoundException("Trip creator not found"));

        log.info("Trip updated: {} by user: {}", trip.getTripCode(), userId);

        return mapToTripDTO(trip, creator);
    }

    @Transactional
    public void cancelTrip(UUID userId, UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (!trip.getCreatorId().equals(userId)) {
            throw new TripAccessDeniedException("Only the trip creator can cancel this trip");
        }

        trip.setStatus(TripStatus.CANCELLED);
        tripRepository.save(trip);

        log.info("Trip cancelled: {} by user: {}", trip.getTripCode(), userId);
    }

    @Transactional(readOnly = true)
    public List<TripDTO> getMyTrips(UUID userId) {
        // Get trips where user is creator
        List<Trip> createdTrips = tripRepository.findByCreatorId(userId);

        // Get trips where user is an approved member (but not creator)
        List<TripMember> memberships = tripMemberRepository.findByUserId(userId);
        List<UUID> memberTripIds = memberships.stream()
                .filter(m -> m.getMemberStatus() == MemberStatus.APPROVED && m.getMemberRole() != MemberRole.CREATOR)
                .map(TripMember::getTripId)
                .toList();

        List<Trip> memberTrips = memberTripIds.isEmpty()
                ? List.of()
                : tripRepository.findAllById(memberTripIds);

        // Combine and deduplicate
        List<Trip> allTrips = new java.util.ArrayList<>(createdTrips);
        memberTrips.stream()
                .filter(t -> allTrips.stream().noneMatch(ct -> ct.getId().equals(t.getId())))
                .forEach(allTrips::add);

        return allTrips.stream()
                .map(trip -> {
                    User creator = userRepository.findById(trip.getCreatorId()).orElse(null);
                    return mapToTripDTO(trip, creator);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TripCardDTO> discoverTrips(String destination, TripType type,
                                            LocalDate fromDate, LocalDate toDate,
                                            int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Trip> trips = tripRepository.discoverTrips(
                TripStatus.OPEN, destination, type, fromDate, toDate, pageRequest);

        return trips.map(trip -> {
            User creator = userRepository.findById(trip.getCreatorId()).orElse(null);
            int memberCount = tripMemberRepository.countByTripIdAndMemberStatus(trip.getId(), MemberStatus.APPROVED);
            return mapToTripCardDTO(trip, creator, memberCount);
        });
    }

    @Transactional
    public TripMemberDTO requestJoin(UUID userId, UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (trip.getStatus() != TripStatus.OPEN) {
            throw new TripAccessDeniedException("This trip is not accepting new members");
        }

        // Check if already a member
        tripMemberRepository.findByTripIdAndUserId(tripId, userId).ifPresent(existing -> {
            throw new AlreadyMemberException("You have already joined or requested to join this trip");
        });

        // Check if trip is full
        int approvedCount = tripMemberRepository.countByTripIdAndMemberStatus(tripId, MemberStatus.APPROVED);
        if (approvedCount >= trip.getMaxSize()) {
            throw new TripFullException("This trip is full");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        MemberStatus initialStatus = trip.getApprovalMode() == ApprovalMode.AUTO
                ? MemberStatus.APPROVED
                : MemberStatus.PENDING;

        TripMember member = TripMember.builder()
                .tripId(tripId)
                .userId(userId)
                .memberRole(MemberRole.MEMBER)
                .memberStatus(initialStatus)
                .build();

        member = tripMemberRepository.save(member);

        log.info("User {} {} trip {}", userId,
                initialStatus == MemberStatus.APPROVED ? "joined" : "requested to join",
                trip.getTripCode());

        // Check if trip is now full after auto-approve
        if (initialStatus == MemberStatus.APPROVED) {
            int newCount = tripMemberRepository.countByTripIdAndMemberStatus(tripId, MemberStatus.APPROVED);
            if (newCount >= trip.getMaxSize()) {
                trip.setStatus(TripStatus.FULL);
                tripRepository.save(trip);
            }
        }

        return mapToTripMemberDTO(member, user);
    }

    @Transactional
    public TripMemberDTO handleJoinRequest(UUID creatorId, UUID tripId, UUID memberId, MemberStatus newStatus) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (!trip.getCreatorId().equals(creatorId)) {
            throw new TripAccessDeniedException("Only the trip creator can manage join requests");
        }

        TripMember member = tripMemberRepository.findById(memberId)
                .orElseThrow(() -> new TripNotFoundException("Member request not found"));

        if (!member.getTripId().equals(tripId)) {
            throw new TripAccessDeniedException("This member request does not belong to this trip");
        }

        // If approving, check capacity
        if (newStatus == MemberStatus.APPROVED) {
            int approvedCount = tripMemberRepository.countByTripIdAndMemberStatus(tripId, MemberStatus.APPROVED);
            if (approvedCount >= trip.getMaxSize()) {
                throw new TripFullException("This trip is full, cannot approve more members");
            }
        }

        member.setMemberStatus(newStatus);
        if (newStatus == MemberStatus.APPROVED) {
            member.setApprovedBy(creatorId);
        }
        member = tripMemberRepository.save(member);

        // Check if trip is now full
        if (newStatus == MemberStatus.APPROVED) {
            int newCount = tripMemberRepository.countByTripIdAndMemberStatus(tripId, MemberStatus.APPROVED);
            if (newCount >= trip.getMaxSize()) {
                trip.setStatus(TripStatus.FULL);
                tripRepository.save(trip);
            }
        }

        User user = userRepository.findById(member.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        log.info("Join request {} for user {} on trip {} by creator {}",
                newStatus, member.getUserId(), trip.getTripCode(), creatorId);

        return mapToTripMemberDTO(member, user);
    }

    @Transactional(readOnly = true)
    public List<TripMemberDTO> getTripMembers(UUID tripId) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        List<TripMember> members = tripMemberRepository.findByTripId(tripId);

        return members.stream()
                .map(member -> {
                    User user = userRepository.findById(member.getUserId()).orElse(null);
                    return mapToTripMemberDTO(member, user);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JoinRequestDTO> getPendingRequests(UUID tripId) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        List<TripMember> pendingMembers = tripMemberRepository
                .findByTripIdAndMemberStatus(tripId, MemberStatus.PENDING);

        return pendingMembers.stream()
                .map(member -> {
                    User user = userRepository.findById(member.getUserId()).orElse(null);
                    return mapToJoinRequestDTO(member, user);
                })
                .toList();
    }

    private String generateTripCode() {
        StringBuilder code = new StringBuilder(TRIP_CODE_LENGTH);
        for (int i = 0; i < TRIP_CODE_LENGTH; i++) {
            code.append(TRIP_CODE_CHARS.charAt(RANDOM.nextInt(TRIP_CODE_CHARS.length())));
        }
        return code.toString();
    }

    private TripDTO mapToTripDTO(Trip trip, User creator) {
        int memberCount = tripMemberRepository.countByTripIdAndMemberStatus(trip.getId(), MemberStatus.APPROVED);
        return TripDTO.builder()
                .id(trip.getId())
                .creatorId(trip.getCreatorId())
                .creatorName(creator != null ? creator.getFirstName() + " " + creator.getLastName() : null)
                .title(trip.getTitle())
                .destination(trip.getDestination())
                .description(trip.getDescription())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .maxSize(trip.getMaxSize())
                .tripType(trip.getTripType())
                .status(trip.getStatus())
                .trustScoreMin(trip.getTrustScoreMin())
                .womenOnly(trip.isWomenOnly())
                .approvalMode(trip.getApprovalMode())
                .tags(trip.getTags())
                .tripCode(trip.getTripCode())
                .coverImageUrl(trip.getCoverImageUrl())
                .memberCount(memberCount)
                .availableSpots(trip.getMaxSize() - memberCount)
                .createdAt(trip.getCreatedAt())
                .updatedAt(trip.getUpdatedAt())
                .build();
    }

    private TripCardDTO mapToTripCardDTO(Trip trip, User creator, int memberCount) {
        return TripCardDTO.builder()
                .id(trip.getId())
                .title(trip.getTitle())
                .destination(trip.getDestination())
                .coverImageUrl(trip.getCoverImageUrl())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .tripType(trip.getTripType())
                .spotsLeft(trip.getMaxSize() - memberCount)
                .maxSize(trip.getMaxSize())
                .memberCount(memberCount)
                .creatorName(creator != null ? creator.getFirstName() + " " + creator.getLastName() : null)
                .womenOnly(trip.isWomenOnly())
                .tags(trip.getTags())
                .build();
    }

    private TripMemberDTO mapToTripMemberDTO(TripMember member, User user) {
        return TripMemberDTO.builder()
                .userId(member.getUserId())
                .firstName(user != null ? user.getFirstName() : null)
                .lastName(user != null ? user.getLastName() : null)
                .role(member.getMemberRole())
                .status(member.getMemberStatus())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private JoinRequestDTO mapToJoinRequestDTO(TripMember member, User user) {
        return JoinRequestDTO.builder()
                .memberId(member.getId())
                .userId(member.getUserId())
                .firstName(user != null ? user.getFirstName() : null)
                .lastName(user != null ? user.getLastName() : null)
                .status(member.getMemberStatus())
                .requestedAt(member.getJoinedAt())
                .build();
    }
}
