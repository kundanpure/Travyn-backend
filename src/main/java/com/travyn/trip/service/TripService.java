package com.travyn.trip.service;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.common.exception.UserNotFoundException;
import com.travyn.notification.entity.NotificationType;
import com.travyn.notification.service.NotificationService;
import com.travyn.trip.dto.*;
import com.travyn.trip.entity.*;
import com.travyn.trip.dto.UpdateTripRequest;
import com.travyn.trip.entity.Trip;
import com.travyn.trip.entity.TripMember;
import com.travyn.trip.entity.TripStatus;
import com.travyn.trip.entity.TripType;
import com.travyn.trip.entity.TripReview;
import com.travyn.trip.dto.TripReviewRequest;
import com.travyn.trip.dto.TripReviewDTO;
import com.travyn.trip.exception.AlreadyMemberException;
import com.travyn.trip.exception.TripAccessDeniedException;
import com.travyn.trip.exception.TripFullException;
import com.travyn.trip.exception.TripNotFoundException;
import com.travyn.trip.repository.TripMemberRepository;
import com.travyn.trip.repository.TripRepository;
import com.travyn.trip.repository.TripReviewRepository;
import com.travyn.trip.repository.TripInviteTokenRepository;
import com.travyn.trip.repository.TripCancellationVoteRepository;
import com.travyn.trip.repository.TripWaypointRepository;
import com.travyn.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripService {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripReviewRepository tripReviewRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ProfileRepository profileRepository;
    private final TripInviteTokenRepository tripInviteTokenRepository;
    private final TripCancellationVoteRepository tripCancellationVoteRepository;
    private final TripWaypointRepository tripWaypointRepository;

    private static final String TRIP_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TRIP_CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public TripDTO createTrip(UUID userId, CreateTripRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        // Validate budget range
        if (request.getMinBudget() != null && request.getMaxBudget() != null
                && request.getMinBudget().compareTo(request.getMaxBudget()) > 0) {
            throw new IllegalArgumentException("Minimum budget must be less than or equal to maximum budget");
        }

        if (request.isWomenOnly()) {
            if (creator.getGender() != com.travyn.auth.entity.Gender.FEMALE || creator.getStatus() != com.travyn.auth.entity.UserStatus.KYC_VERIFIED) {
                throw new IllegalArgumentException("Only KYC-verified women can create women-only trips");
            }
        }

        // Check for overlapping trips
        if (tripRepository.hasOverlappingTrips(userId, request.getStartDate(), request.getEndDate(), null)) {
            throw new TripAccessDeniedException("You already have an approved trip scheduled during these dates");
        }

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
                .minBudget(request.getMinBudget())
                .maxBudget(request.getMaxBudget())
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

    @Transactional
    public TripDTO getTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        // Auto-close: if end date has passed and trip is still OPEN or FULL, mark as COMPLETED
        if ((trip.getStatus() == TripStatus.OPEN || trip.getStatus() == TripStatus.FULL)
                && trip.getEndDate().isBefore(LocalDate.now())) {
            trip.setStatus(TripStatus.COMPLETED);
            trip = tripRepository.save(trip);
            log.info("Trip auto-completed: {} (end date passed)", trip.getTripCode());
        }

        User creator = userRepository.findById(trip.getCreatorId())
                .orElseThrow(() -> new UserNotFoundException("Trip creator not found"));

        return mapToTripDTO(trip, creator);
    }

    @Transactional
    public TripDTO updateTrip(UUID userId, UUID tripId, UpdateTripRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (!trip.getCreatorId().equals(userId)) {
            throw new TripAccessDeniedException("Only the trip creator can update the trip");
        }

        if (tripRepository.hasOverlappingTrips(userId, request.getStartDate(), request.getEndDate(), tripId)) {
            throw new TripAccessDeniedException("You already have another approved trip scheduled during these new dates");
        }

        // Allow partial updates on completed or cancelled trips
        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new TripAccessDeniedException("Cannot edit a trip that is " + trip.getStatus().name().toLowerCase());
        }
        
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Trip creator not found"));

        if (request.getWomenOnly() != null && request.getWomenOnly()) {
            if (creator.getGender() != com.travyn.auth.entity.Gender.FEMALE || creator.getStatus() != com.travyn.auth.entity.UserStatus.KYC_VERIFIED) {
                throw new IllegalArgumentException("Only KYC-verified women can make a trip women-only");
            }
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
        if (request.getMinBudget() != null) {
            trip.setMinBudget(request.getMinBudget());
        }
        if (request.getMaxBudget() != null) {
            trip.setMaxBudget(request.getMaxBudget());
        }

        trip = tripRepository.save(trip);

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

    @Transactional
    public void transferOwnership(UUID currentCreatorId, UUID tripId, UUID newCreatorId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (!trip.getCreatorId().equals(currentCreatorId)) {
            throw new TripAccessDeniedException("Only the current trip creator can transfer ownership");
        }

        TripMember newCreatorMember = tripMemberRepository.findByTripIdAndUserId(tripId, newCreatorId)
                .orElseThrow(() -> new UserNotFoundException("The selected user is not a member of this trip"));

        if (newCreatorMember.getMemberStatus() != MemberStatus.APPROVED) {
            throw new TripAccessDeniedException("Ownership can only be transferred to an approved member");
        }

        TripMember currentCreatorMember = tripMemberRepository.findByTripIdAndUserId(tripId, currentCreatorId)
                .orElseThrow(() -> new UserNotFoundException("Creator member record not found"));

        // Demote current creator
        currentCreatorMember.setMemberRole(MemberRole.MEMBER);
        tripMemberRepository.save(currentCreatorMember);

        // Promote new creator
        newCreatorMember.setMemberRole(MemberRole.CREATOR);
        tripMemberRepository.save(newCreatorMember);

        // Update trip
        trip.setCreatorId(newCreatorId);
        tripRepository.save(trip);

        log.info("Trip ownership transferred: {} from {} to {}", trip.getTripCode(), currentCreatorId, newCreatorId);

        notificationService.notifyUser(
                newCreatorId,
                "You are now the Admin of \"" + trip.getTitle() + "\"! 👑",
                NotificationType.JOIN_APPROVED,
                tripId
        );
    }

    @Transactional
    public List<TripDTO> getMyTrips(UUID userId) {
        // Get ALL memberships for this user (CREATOR, APPROVED, PENDING, etc.)
        List<TripMember> memberships = tripMemberRepository.findByUserId(userId);

        if (memberships.isEmpty()) {
            return List.of();
        }

        List<UUID> tripIds = memberships.stream().map(TripMember::getTripId).toList();
        List<Trip> trips = tripRepository.findAllById(tripIds);

        return trips.stream()
                .map(trip -> {
                    // Auto-close: if end date has passed and trip is still OPEN or FULL, mark as COMPLETED
                    if ((trip.getStatus() == TripStatus.OPEN || trip.getStatus() == TripStatus.FULL)
                            && trip.getEndDate().isBefore(LocalDate.now())) {
                        trip.setStatus(TripStatus.COMPLETED);
                        tripRepository.save(trip);
                        log.info("Trip auto-completed: {} (end date passed)", trip.getTripCode());
                    }

                    User creator = userRepository.findById(trip.getCreatorId()).orElse(null);
                    TripDTO dto = mapToTripDTO(trip, creator);

                    // Attach the current user's membership info
                    memberships.stream()
                            .filter(m -> m.getTripId().equals(trip.getId()))
                            .findFirst()
                            .ifPresent(m -> {
                                dto.setMemberRole(m.getMemberRole().name());
                                dto.setMemberStatus(m.getMemberStatus().name());
                            });

                    return dto;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TripCardDTO> discoverTrips(String destination, TripType type,
                                            LocalDate fromDate, LocalDate toDate,
                                            boolean isVerifiedWoman,
                                            String statusFilter,
                                            int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<TripStatus> statuses = null;
        boolean isUpcoming = false;
        boolean isOngoing = false;

        if ("COMPLETED".equalsIgnoreCase(statusFilter)) {
            statuses = List.of(TripStatus.COMPLETED);
        } else if ("UPCOMING".equalsIgnoreCase(statusFilter)) {
            isUpcoming = true;
            statuses = List.of(TripStatus.OPEN, TripStatus.FULL);
        } else if ("ONGOING".equalsIgnoreCase(statusFilter)) {
            isOngoing = true;
            statuses = List.of(TripStatus.OPEN, TripStatus.FULL);
        } else if ("OPEN".equalsIgnoreCase(statusFilter)) {
            statuses = List.of(TripStatus.OPEN);
        } else if ("CLOSED".equalsIgnoreCase(statusFilter)) {
            statuses = List.of(TripStatus.FULL, TripStatus.CANCELLED);
        } else if ("ALL".equalsIgnoreCase(statusFilter)) {
            statuses = null;
        } else {
            // Default to OPEN if an invalid string is provided
            statuses = List.of(TripStatus.OPEN);
        }

        Page<Trip> trips = tripRepository.discoverTrips(
                statuses, destination, type, fromDate, toDate, isVerifiedWoman, isUpcoming, isOngoing, pageRequest);

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

        // Block join requests within 1 day of the trip start
        LocalDate joinCutoff = trip.getStartDate().minusDays(1);
        if (!LocalDate.now().isBefore(joinCutoff)) {
            throw new TripAccessDeniedException(
                "Join requests close 1 day before the trip starts to give everyone time to prepare");
        }

        // Check if already a member
        tripMemberRepository.findByTripIdAndUserId(tripId, userId).ifPresent(existing -> {
            throw new AlreadyMemberException("You have already joined or requested to join this trip");
        });

        // Check if user is already in an ongoing trip
        if (tripMemberRepository.isUserInOngoingTrip(userId)) {
            throw new AlreadyMemberException("You are already in an ongoing trip. Complete or leave your current trip before joining a new one.");
        }

        // Check if trip is full
        int approvedCount = tripMemberRepository.countByTripIdAndMemberStatus(tripId, MemberStatus.APPROVED);
        if (approvedCount >= trip.getMaxSize()) {
            throw new TripFullException("This trip is full");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (trip.isWomenOnly()) {
            if (user.getGender() != com.travyn.auth.entity.Gender.FEMALE || user.getStatus() != com.travyn.auth.entity.UserStatus.KYC_VERIFIED) {
                throw new TripAccessDeniedException("This is a women-only trip. Only KYC-verified women can join.");
            }
        }

        MemberStatus initialStatus = trip.getApprovalMode() == ApprovalMode.AUTO
                ? MemberStatus.APPROVED
                : MemberStatus.PENDING;

        if (initialStatus == MemberStatus.APPROVED) {
            if (tripRepository.hasOverlappingTrips(userId, trip.getStartDate(), trip.getEndDate(), null)) {
                throw new TripAccessDeniedException("You already have an approved trip scheduled during these dates");
            }
        }

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

        // Notify the trip creator about the join request
        if (initialStatus == MemberStatus.PENDING) {
            notificationService.notifyUser(
                    trip.getCreatorId(),
                    user.getFirstName() + " " + user.getLastName() + " wants to join \"" + trip.getTitle() + "\"",
                    NotificationType.JOIN_REQUEST,
                    tripId
            );
        } else if (initialStatus == MemberStatus.APPROVED) {
            // Broadcast to existing approved members
            List<TripMember> currentMembers = tripMemberRepository.findByTripId(tripId);
            for (TripMember m : currentMembers) {
                if (m.getMemberStatus() == MemberStatus.APPROVED && 
                    !m.getUserId().equals(userId) && 
                    !m.getUserId().equals(trip.getCreatorId())) {
                    
                    notificationService.notifyUser(
                            m.getUserId(),
                            "Say hi to " + user.getFirstName() + ", the newest member of \"" + trip.getTitle() + "\"! 🎉",
                            NotificationType.NEW_MEMBER_JOINED,
                            tripId
                    );
                }
            }
        }
        return mapToTripMemberDTO(member, user);
    }

    @Transactional
    public TripMemberDTO handleJoinRequest(UUID creatorId, UUID tripId, UUID memberId, MemberStatus newStatus) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (!trip.getCreatorId().equals(creatorId)) {
            throw new TripAccessDeniedException("Only the trip creator can handle join requests");
        }

        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, memberId)
                .orElseThrow(() -> new UserNotFoundException("Member request not found"));

        if (member.getMemberStatus() != MemberStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be handled");
        }

        // If approving, check capacity and overlap
        if (newStatus == MemberStatus.APPROVED) {
            if (tripRepository.hasOverlappingTrips(member.getUserId(), trip.getStartDate(), trip.getEndDate(), null)) {
                throw new TripAccessDeniedException("Cannot approve: This user has already committed to another trip on these dates");
            }

            int approvedCount = tripMemberRepository.countByTripIdAndMemberStatus(tripId, MemberStatus.APPROVED);
            if (approvedCount >= trip.getMaxSize()) {
                throw new TripFullException("This trip is already full");
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

        // Notify the user about their join request decision
        if (newStatus == MemberStatus.APPROVED) {
            notificationService.notifyUser(
                    member.getUserId(),
                    "Your request to join \"" + trip.getTitle() + "\" has been approved! 🎉",
                    NotificationType.JOIN_APPROVED,
                    tripId
            );

            // Broadcast to existing approved members
            List<TripMember> currentMembers = tripMemberRepository.findByTripId(tripId);
            for (TripMember m : currentMembers) {
                if (m.getMemberStatus() == MemberStatus.APPROVED && 
                    !m.getUserId().equals(member.getUserId()) && 
                    !m.getUserId().equals(trip.getCreatorId())) {
                    
                    notificationService.notifyUser(
                            m.getUserId(),
                            "Say hi to " + user.getFirstName() + ", the newest member of \"" + trip.getTitle() + "\"! 🎉",
                            NotificationType.NEW_MEMBER_JOINED,
                            tripId
                    );
                }
            }
        } else if (newStatus == MemberStatus.REJECTED) {
            notificationService.notifyUser(
                    member.getUserId(),
                    "Your request to join \"" + trip.getTitle() + "\" was declined.",
                    NotificationType.JOIN_REJECTED,
                    tripId
            );
        }

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
    public List<JoinRequestDTO> getPendingRequests(UUID userId, UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (!trip.getCreatorId().equals(userId)) {
            throw new TripAccessDeniedException("Only the trip creator can view pending requests");
        }

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

    @Transactional
    public void leaveTrip(UUID userId, UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (trip.getCreatorId().equals(userId)) {
            throw new TripAccessDeniedException("The trip creator cannot leave the trip. You must cancel the trip instead.");
        }

        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> new UserNotFoundException("You are not a member of this trip"));

        // Check deadline: must leave before 1 day prior to start date
        LocalDate cutoffDate = trip.getStartDate().minusDays(1);
        if (!LocalDate.now().isBefore(cutoffDate)) {
            throw new TripAccessDeniedException("It is too late to leave this trip. You must withdraw before " + cutoffDate.toString() + ".");
        }

        MemberStatus previousStatus = member.getMemberStatus();
        tripMemberRepository.delete(member);

        log.info("User {} left trip {}", userId, trip.getTripCode());

        // If trip was full and an approved member left, open it up
        if (previousStatus == MemberStatus.APPROVED && trip.getStatus() == TripStatus.FULL) {
            trip.setStatus(TripStatus.OPEN);
            tripRepository.save(trip);
            log.info("Trip {} status changed from FULL to OPEN", trip.getTripCode());
        }

        // Broadcast to existing members if the user was APPROVED
        if (previousStatus == MemberStatus.APPROVED) {
            User user = userRepository.findById(userId).orElse(null);
            String userName = user != null ? user.getFirstName() : "A member";
            
            List<TripMember> currentMembers = tripMemberRepository.findByTripId(tripId);
            for (TripMember m : currentMembers) {
                if (m.getMemberStatus() == MemberStatus.APPROVED) {
                    notificationService.notifyUser(
                            m.getUserId(),
                            userName + " has left \"" + trip.getTitle() + "\".",
                            NotificationType.MEMBER_LEFT,
                            tripId
                    );
                }
            }
        }
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
                .minBudget(trip.getMinBudget())
                .maxBudget(trip.getMaxBudget())
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
                .creatorVerified(creator != null && creator.getStatus() == com.travyn.auth.entity.UserStatus.KYC_VERIFIED)
                .womenOnly(trip.isWomenOnly())
                .tags(trip.getTags())
                .minBudget(trip.getMinBudget())
                .maxBudget(trip.getMaxBudget())
                .build();
    }

    private TripMemberDTO mapToTripMemberDTO(TripMember member, User user) {
        String photoUrl = null;
        if (user != null) {
            photoUrl = profileRepository.findByUserId(user.getId())
                    .map(com.travyn.profile.entity.Profile::getProfilePhotoUrl)
                    .orElse(null);
        }

        return TripMemberDTO.builder()
                .userId(member.getUserId())
                .firstName(user != null ? user.getFirstName() : null)
                .lastName(user != null ? user.getLastName() : null)
                .role(member.getMemberRole())
                .status(member.getMemberStatus())
                .joinedAt(member.getJoinedAt())
                .profilePhotoUrl(photoUrl)
                .verified(user != null && user.getStatus() == com.travyn.auth.entity.UserStatus.KYC_VERIFIED)
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
                .verified(user != null && user.getStatus() == com.travyn.auth.entity.UserStatus.KYC_VERIFIED)
                .build();
    }

    @Transactional
    public TripReviewDTO submitTripReview(UUID userId, UUID tripId, TripReviewRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (trip.getStatus() != TripStatus.COMPLETED) {
            throw new IllegalStateException("Can only review completed trips");
        }

        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripAccessDeniedException("You are not a member of this trip"));

        if (member.getMemberStatus() != MemberStatus.APPROVED) {
            throw new TripAccessDeniedException("Only approved members can review the trip");
        }

        if (tripReviewRepository.findByTripIdAndReviewerId(tripId, userId).isPresent()) {
            throw new IllegalStateException("You have already reviewed this trip");
        }

        User reviewer = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Reviewer not found"));

        TripReview review = TripReview.builder()
                .trip(trip)
                .reviewer(reviewer)
                .rating(request.getRating())
                .textReview(request.getTextReview())
                .build();

        review = tripReviewRepository.save(review);
        return mapToTripReviewDTO(review);
    }

    @Transactional(readOnly = true)
    public List<TripReviewDTO> getTripReviews(UUID tripId) {
        return tripReviewRepository.findByTripIdOrderByCreatedAtDesc(tripId).stream()
                .map(this::mapToTripReviewDTO)
                .toList();
    }

    private TripReviewDTO mapToTripReviewDTO(TripReview review) {
        return TripReviewDTO.builder()
                .id(review.getId())
                .tripId(review.getTrip().getId())
                .reviewerId(review.getReviewer().getId())
                .reviewerName(review.getReviewer().getFirstName() + " " + review.getReviewer().getLastName())
                .reviewerAvatarUrl(null)
                .rating(review.getRating())
                .textReview(review.getTextReview())
                .createdAt(review.getCreatedAt())
                .build();
    }

    // ---------------------------------------------------------------
    //  TRIP INVITE LINK METHODS
    // ---------------------------------------------------------------

    @Transactional
    public TripInviteTokenDTO generateInviteLink(UUID userId, UUID tripId, InviteLinkRequest request, String baseUrl) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (!trip.getCreatorId().equals(userId)) {
            tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                    .filter(m -> m.getMemberStatus() == MemberStatus.APPROVED)
                    .orElseThrow(() -> new TripAccessDeniedException("Only approved trip members can generate invite links"));
        }

        if (trip.getStatus() == TripStatus.CANCELLED || trip.getStatus() == TripStatus.COMPLETED) {
            throw new TripAccessDeniedException("Cannot generate invite links for cancelled or completed trips");
        }

        byte[] randomBytes = new byte[16];
        RANDOM.nextBytes(randomBytes);
        String token = HexFormat.of().formatHex(randomBytes);

        int expiryDays = request.getExpiresInDays() > 0 ? request.getExpiresInDays() : 7;

        TripInviteToken inviteToken = TripInviteToken.builder()
                .tripId(tripId)
                .invitedBy(userId)
                .token(token)
                .maxUses(request.getMaxUses())
                .autoApprove(request.isAutoApprove())
                .expiresAt(Instant.now().plus(expiryDays, ChronoUnit.DAYS))
                .build();

        inviteToken = tripInviteTokenRepository.save(inviteToken);
        log.info("Invite link generated for trip {} by user {}", trip.getTripCode(), userId);

        return mapToInviteTokenDTO(inviteToken, baseUrl);
    }

    @Transactional(readOnly = true)
    public TripInvitePreviewDTO previewInvite(String token) {
        TripInviteToken inviteToken = tripInviteTokenRepository.findByTokenAndIsActiveTrue(token)
                .orElseThrow(() -> new TripNotFoundException("Invalid or expired invite link"));

        Trip trip = tripRepository.findById(inviteToken.getTripId())
                .orElseThrow(() -> new TripNotFoundException("Trip no longer exists"));

        User creator = userRepository.findById(trip.getCreatorId())
                .orElseThrow(() -> new UserNotFoundException("Trip creator not found"));

        User inviter = userRepository.findById(inviteToken.getInvitedBy())
                .orElseThrow(() -> new UserNotFoundException("Inviter not found"));

        int memberCount = tripMemberRepository.countByTripIdAndMemberStatus(trip.getId(), MemberStatus.APPROVED);
        boolean isExpired = Instant.now().isAfter(inviteToken.getExpiresAt());
        boolean isFull = memberCount >= trip.getMaxSize();

        String creatorPhoto = profileRepository.findByUserId(creator.getId())
                .map(p -> p.getProfilePhotoUrl())
                .orElse(null);

        return TripInvitePreviewDTO.builder()
                .tripId(trip.getId())
                .tripTitle(trip.getTitle())
                .destination(trip.getDestination())
                .description(trip.getDescription())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .tripType(trip.getTripType().name())
                .creatorName(creator.getFirstName() + " " + creator.getLastName())
                .creatorProfilePhoto(creatorPhoto)
                .creatorVerified(creator.getStatus() == com.travyn.auth.entity.UserStatus.KYC_VERIFIED)
                .memberCount(memberCount)
                .maxSize(trip.getMaxSize())
                .coverImageUrl(trip.getCoverImageUrl())
                .womenOnly(trip.isWomenOnly())
                .isFull(isFull)
                .isExpired(isExpired)
                .invitedByName(inviter.getFirstName() + " " + inviter.getLastName())
                .build();
    }

    @Transactional
    public TripMemberDTO acceptInvite(UUID userId, String token) {
        TripInviteToken inviteToken = tripInviteTokenRepository.findByTokenAndIsActiveTrue(token)
                .orElseThrow(() -> new TripNotFoundException("Invalid or expired invite link"));

        if (Instant.now().isAfter(inviteToken.getExpiresAt())) {
            throw new TripAccessDeniedException("This invite link has expired");
        }

        if (inviteToken.getMaxUses() > 0 && inviteToken.getUsedCount() >= inviteToken.getMaxUses()) {
            throw new TripAccessDeniedException("This invite link has reached its usage limit");
        }

        Trip trip = tripRepository.findById(inviteToken.getTripId())
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (trip.getStatus() == TripStatus.CANCELLED || trip.getStatus() == TripStatus.COMPLETED) {
            throw new TripAccessDeniedException("This trip is no longer active");
        }

        tripMemberRepository.findByTripIdAndUserId(trip.getId(), userId).ifPresent(existing -> {
            throw new AlreadyMemberException("You are already a member of this trip");
        });

        int approvedCount = tripMemberRepository.countByTripIdAndMemberStatus(trip.getId(), MemberStatus.APPROVED);
        if (approvedCount >= trip.getMaxSize()) {
            throw new TripFullException("This trip is full");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (trip.isWomenOnly()) {
            if (user.getGender() != com.travyn.auth.entity.Gender.FEMALE
                    || user.getStatus() != com.travyn.auth.entity.UserStatus.KYC_VERIFIED) {
                throw new TripAccessDeniedException("This is a women-only trip. Only KYC-verified women can join.");
            }
        }

        MemberStatus initialStatus = inviteToken.isAutoApprove()
                ? MemberStatus.APPROVED
                : (trip.getApprovalMode() == ApprovalMode.AUTO ? MemberStatus.APPROVED : MemberStatus.PENDING);

        if (initialStatus == MemberStatus.APPROVED) {
            if (tripRepository.hasOverlappingTrips(userId, trip.getStartDate(), trip.getEndDate(), null)) {
                throw new TripAccessDeniedException("You already have an approved trip scheduled during these dates");
            }
        }

        TripMember member = TripMember.builder()
                .tripId(trip.getId())
                .userId(userId)
                .memberRole(MemberRole.MEMBER)
                .memberStatus(initialStatus)
                .build();

        member = tripMemberRepository.save(member);

        inviteToken.setUsedCount(inviteToken.getUsedCount() + 1);
        tripInviteTokenRepository.save(inviteToken);

        log.info("User {} joined trip {} via invite link", userId, trip.getTripCode());

        if (initialStatus == MemberStatus.APPROVED) {
            int newCount = tripMemberRepository.countByTripIdAndMemberStatus(trip.getId(), MemberStatus.APPROVED);
            if (newCount >= trip.getMaxSize()) {
                trip.setStatus(TripStatus.FULL);
                tripRepository.save(trip);
            }
        }

        notificationService.notifyUser(
                inviteToken.getInvitedBy(),
                user.getFirstName() + " joined \"" + trip.getTitle() + "\" via your invite link!",
                NotificationType.TRIP_INVITE_ACCEPTED,
                trip.getId()
        );

        if (!inviteToken.getInvitedBy().equals(trip.getCreatorId())) {
            notificationService.notifyUser(
                    trip.getCreatorId(),
                    user.getFirstName() + " joined \"" + trip.getTitle() + "\" via invite link",
                    initialStatus == MemberStatus.APPROVED
                            ? NotificationType.NEW_MEMBER_JOINED
                            : NotificationType.JOIN_REQUEST,
                    trip.getId()
            );
        }

        return mapToTripMemberDTO(member, user);
    }

    @Transactional
    public void revokeInviteLink(UUID userId, UUID tripId, UUID inviteTokenId) {
        Trip revokeTrip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (!revokeTrip.getCreatorId().equals(userId)) {
            throw new TripAccessDeniedException("Only the trip creator can revoke invite links");
        }

        TripInviteToken revokeTarget = tripInviteTokenRepository.findById(inviteTokenId)
                .orElseThrow(() -> new TripNotFoundException("Invite link not found"));

        if (!revokeTarget.getTripId().equals(tripId)) {
            throw new TripAccessDeniedException("Invite link does not belong to this trip");
        }

        revokeTarget.setActive(false);
        tripInviteTokenRepository.save(revokeTarget);
        log.info("Invite link {} revoked for trip {} by user {}", inviteTokenId, revokeTrip.getTripCode(), userId);
    }

    @Transactional(readOnly = true)
    public List<TripInviteTokenDTO> getInviteLinks(UUID userId, UUID tripId, String baseUrl) {
        Trip linksTrip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (!linksTrip.getCreatorId().equals(userId)) {
            throw new TripAccessDeniedException("Only the trip creator can view invite links");
        }

        return tripInviteTokenRepository.findByTripIdAndIsActiveTrueOrderByCreatedAtDesc(tripId)
                .stream()
                .map(t -> mapToInviteTokenDTO(t, baseUrl))
                .toList();
    }

    private TripInviteTokenDTO mapToInviteTokenDTO(TripInviteToken invToken, String baseUrl) {
        return TripInviteTokenDTO.builder()
                .id(invToken.getId())
                .token(invToken.getToken())
                .link(baseUrl + "/invite/" + invToken.getToken())
                .maxUses(invToken.getMaxUses())
                .usedCount(invToken.getUsedCount())
                .autoApprove(invToken.isAutoApprove())
                .isActive(invToken.isActive())
                .expiresAt(invToken.getExpiresAt())
                .createdAt(invToken.getCreatedAt())
                .build();
    }

    @Transactional
    public void deleteTrip(UUID userId, UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (!trip.getCreatorId().equals(userId)) {
            throw new TripAccessDeniedException("Only the trip creator can delete the trip");
        }

        int approvedCount = tripMemberRepository.countByTripIdAndMemberStatus(tripId, MemberStatus.APPROVED);
        if (approvedCount > 1) { // 1 is the creator
            throw new IllegalStateException("Cannot delete a trip with active members. Cancel it instead.");
        }

        tripCancellationVoteRepository.deleteByTripId(tripId);
        tripInviteTokenRepository.deleteByTripId(tripId);
        tripMemberRepository.deleteByTripId(tripId);
        tripReviewRepository.deleteByTripId(tripId);
        tripWaypointRepository.deleteByTripId(tripId);
        
        tripRepository.delete(trip);
        log.info("User {} deleted trip {}", userId, tripId);
    }

    @Transactional
    public void initiateCancellation(UUID userId, UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));

        if (!trip.getCreatorId().equals(userId)) {
            throw new TripAccessDeniedException("Only the trip creator can cancel the trip");
        }

        List<TripMember> approvedMembers = tripMemberRepository.findByTripIdAndMemberStatus(tripId, MemberStatus.APPROVED);
        
        if (approvedMembers.size() <= 1) {
            // Only creator is here, cancel instantly
            trip.setStatus(TripStatus.CANCELLED);
            tripRepository.save(trip);
            return;
        }

        trip.setStatus(TripStatus.CANCELLATION_PENDING);
        tripRepository.save(trip);

        for (TripMember m : approvedMembers) {
            if (!m.getUserId().equals(userId)) {
                TripCancellationVote vote = TripCancellationVote.builder()
                        .trip(trip)
                        .userId(m.getUserId())
                        .vote(VoteStatus.PENDING)
                        .build();
                tripCancellationVoteRepository.save(vote);

                notificationService.notifyUser(
                        m.getUserId(),
                        "The creator wants to cancel '" + trip.getTitle() + "'. Please vote to confirm.",
                        NotificationType.CANCELLATION_VOTE,
                        tripId
                );
            }
        }
    }

    @Transactional
    public void submitCancellationVote(UUID userId, UUID tripId, String voteStatusStr) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));
                
        if (trip.getStatus() != TripStatus.CANCELLATION_PENDING) {
            throw new IllegalStateException("Trip is not pending cancellation");
        }

        TripCancellationVote vote = tripCancellationVoteRepository.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> new TripAccessDeniedException("No pending vote found for this user"));

        vote.setVote(VoteStatus.valueOf(voteStatusStr.toUpperCase()));
        tripCancellationVoteRepository.save(vote);

        List<TripCancellationVote> allVotes = tripCancellationVoteRepository.findByTripId(tripId);
        boolean allVoted = allVotes.stream().noneMatch(v -> v.getVote() == VoteStatus.PENDING);

        if (allVoted) {
            long yesCount = allVotes.stream().filter(v -> v.getVote() == VoteStatus.YES).count();
            if (yesCount > allVotes.size() / 2) {
                // Majority YES -> Cancel Trip
                trip.setStatus(TripStatus.CANCELLED);
                tripRepository.save(trip);
                
                // Notify everyone
                List<TripMember> approvedMembers = tripMemberRepository.findByTripIdAndMemberStatus(tripId, MemberStatus.APPROVED);
                for (TripMember m : approvedMembers) {
                    notificationService.notifyUser(
                            m.getUserId(),
                            "Trip '" + trip.getTitle() + "' has been officially cancelled.",
                            NotificationType.TRIP_CANCELLED,
                            tripId
                    );
                }
            } else {
                // Majority NO or Tie -> Revert to OPEN/FULL
                int approvedCount = tripMemberRepository.countByTripIdAndMemberStatus(tripId, MemberStatus.APPROVED);
                if (approvedCount >= trip.getMaxSize()) {
                    trip.setStatus(TripStatus.FULL);
                } else {
                    trip.setStatus(TripStatus.OPEN);
                }
                tripRepository.save(trip);
                
                // Clear votes
                tripCancellationVoteRepository.deleteByTripId(tripId);
                
                notificationService.notifyUser(
                        trip.getCreatorId(),
                        "The group voted NO. Cancellation of '" + trip.getTitle() + "' was rejected.",
                        NotificationType.TRIP_CANCELLED, // using as general notification
                        tripId
                );
            }
        }
    }
}
