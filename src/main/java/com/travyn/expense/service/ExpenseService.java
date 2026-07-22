package com.travyn.expense.service;

import com.travyn.auth.entity.User;
import com.travyn.auth.repository.UserRepository;
import com.travyn.expense.dto.*;
import com.travyn.expense.entity.Expense;
import com.travyn.expense.entity.ExpenseCategory;
import com.travyn.expense.entity.ExpenseSplit;
import com.travyn.expense.entity.SplitType;
import com.travyn.expense.exception.ExpenseAccessDeniedException;
import com.travyn.expense.exception.ExpenseNotFoundException;
import com.travyn.expense.repository.ExpenseRepository;
import com.travyn.expense.repository.ExpenseSplitRepository;
import com.travyn.trip.entity.MemberStatus;
import com.travyn.trip.entity.TripMember;
import com.travyn.trip.exception.TripNotFoundException;
import com.travyn.trip.repository.TripMemberRepository;
import com.travyn.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import com.travyn.chat.dto.SendMessageRequest;
import com.travyn.chat.service.ChatService;
import com.travyn.expense.entity.*;
import com.travyn.expense.repository.TripSettlementRepository;
import com.travyn.notification.entity.NotificationType;
import com.travyn.notification.service.NotificationService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository splitRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final TripSettlementRepository settlementRepository;
    private final NotificationService notificationService;
    private final ChatService chatService;

    @Transactional(readOnly = true)
    public List<ExpenseDTO> getExpenses(UUID userId, UUID tripId) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));
        validateMembership(userId, tripId);

        List<Expense> expenses = expenseRepository.findByTripIdOrderByDateDesc(tripId);
        if (expenses.isEmpty()) return List.of();

        List<UUID> expenseIds = expenses.stream().map(Expense::getId).toList();
        List<ExpenseSplit> allSplits = splitRepository.findByExpenseIdIn(expenseIds);
        Map<UUID, List<ExpenseSplit>> splitsByExpense = allSplits.stream()
                .collect(Collectors.groupingBy(ExpenseSplit::getExpenseId));

        // Resolve user names
        Set<UUID> userIds = new HashSet<>();
        expenses.forEach(e -> userIds.add(e.getPaidBy()));
        allSplits.forEach(s -> userIds.add(s.getUserId()));
        Map<UUID, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return expenses.stream().map(expense -> {
            User payer = usersById.get(expense.getPaidBy());
            List<ExpenseSplit> splits = splitsByExpense.getOrDefault(expense.getId(), List.of());

            List<ExpenseDTO.SplitDTO> splitDTOs = splits.stream().map(split -> {
                User splitUser = usersById.get(split.getUserId());
                return ExpenseDTO.SplitDTO.builder()
                        .id(split.getId())
                        .userId(split.getUserId())
                        .userName(splitUser != null ? splitUser.getFirstName() + " " + splitUser.getLastName() : null)
                        .amount(split.getAmount())
                        .build();
            }).toList();

            return ExpenseDTO.builder()
                    .id(expense.getId())
                    .tripId(expense.getTripId())
                    .paidBy(expense.getPaidBy())
                    .paidByName(payer != null ? payer.getFirstName() + " " + payer.getLastName() : null)
                    .title(expense.getTitle())
                    .amount(expense.getAmount())
                    .currency(expense.getCurrency())
                    .category(expense.getCategory())
                    .splitType(expense.getSplitType())
                    .date(expense.getDate())
                    .notes(expense.getNotes())
                    .splits(splitDTOs)
                    .createdAt(expense.getCreatedAt())
                    .build();
        }).toList();
    }

    @Transactional
    public ExpenseDTO addExpense(UUID userId, UUID tripId, CreateExpenseRequest request) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));
        validateMembership(userId, tripId);

        SplitType splitType = request.getSplitType() != null ? request.getSplitType() : SplitType.EQUAL;

        Expense expense = Expense.builder()
                .tripId(tripId)
                .paidBy(userId)
                .title(request.getTitle().trim())
                .amount(request.getAmount())
                .currency("INR")
                .category(request.getCategory() != null ? request.getCategory() : ExpenseCategory.OTHER)
                .splitType(splitType)
                .date(request.getDate())
                .notes(request.getNotes())
                .build();

        expense = expenseRepository.save(expense);

        // Determine who to split with
        List<UUID> splitUserIds;
        if (request.getSplitWith() != null && !request.getSplitWith().isEmpty()) {
            splitUserIds = request.getSplitWith();
        } else {
            // Split among all approved members
            splitUserIds = tripMemberRepository.findByTripId(tripId).stream()
                    .filter(m -> m.getMemberStatus() == MemberStatus.APPROVED)
                    .map(TripMember::getUserId)
                    .toList();
        }

        // Create splits
        List<ExpenseSplit> splits = new ArrayList<>();
        if (splitType == SplitType.CUSTOM && request.getCustomAmounts() != null) {
            for (Map.Entry<UUID, BigDecimal> entry : request.getCustomAmounts().entrySet()) {
                splits.add(ExpenseSplit.builder()
                        .expenseId(expense.getId())
                        .userId(entry.getKey())
                        .amount(entry.getValue())
                        .build());
            }
        } else {
            // Equal split
            BigDecimal perPerson = request.getAmount()
                    .divide(BigDecimal.valueOf(splitUserIds.size()), 2, RoundingMode.HALF_UP);

            // Handle rounding: give the remainder to the first person
            BigDecimal remainder = request.getAmount()
                    .subtract(perPerson.multiply(BigDecimal.valueOf(splitUserIds.size())));

            for (int i = 0; i < splitUserIds.size(); i++) {
                BigDecimal splitAmount = i == 0 ? perPerson.add(remainder) : perPerson;
                splits.add(ExpenseSplit.builder()
                        .expenseId(expense.getId())
                        .userId(splitUserIds.get(i))
                        .amount(splitAmount)
                        .build());
            }
        }

        splits = splitRepository.saveAll(splits);
        log.info("Expense '{}' of {} INR added to trip {} by user {}", expense.getTitle(), expense.getAmount(), tripId, userId);

        // Build response
        User payer = userRepository.findById(userId).orElse(null);
        Map<UUID, User> usersById = userRepository.findAllById(
                splits.stream().map(ExpenseSplit::getUserId).toList()
        ).stream().collect(Collectors.toMap(User::getId, u -> u));

        List<ExpenseDTO.SplitDTO> splitDTOs = splits.stream().map(s -> {
            User u = usersById.get(s.getUserId());
            return ExpenseDTO.SplitDTO.builder()
                    .id(s.getId())
                    .userId(s.getUserId())
                    .userName(u != null ? u.getFirstName() + " " + u.getLastName() : null)
                    .amount(s.getAmount())
                    .build();
        }).toList();

        return ExpenseDTO.builder()
                .id(expense.getId())
                .tripId(expense.getTripId())
                .paidBy(expense.getPaidBy())
                .paidByName(payer != null ? payer.getFirstName() + " " + payer.getLastName() : null)
                .title(expense.getTitle())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .category(expense.getCategory())
                .splitType(expense.getSplitType())
                .date(expense.getDate())
                .notes(expense.getNotes())
                .splits(splitDTOs)
                .createdAt(expense.getCreatedAt())
                .build();
    }

    @Transactional
    public ExpenseDTO updateExpense(UUID userId, UUID tripId, UUID expenseId, CreateExpenseRequest request) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));
        validateMembership(userId, tripId);

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found"));

        if (!expense.getTripId().equals(tripId)) {
            throw new ExpenseAccessDeniedException("Expense does not belong to this trip");
        }

        if (!expense.getPaidBy().equals(userId)) {
            boolean isCreator = tripRepository.findById(tripId)
                    .map(t -> t.getCreatorId().equals(userId))
                    .orElse(false);
            if (!isCreator) {
                throw new ExpenseAccessDeniedException("Only the payer or trip creator can edit this expense");
            }
        }

        SplitType splitType = request.getSplitType() != null ? request.getSplitType() : SplitType.EQUAL;

        expense.setTitle(request.getTitle().trim());
        expense.setAmount(request.getAmount());
        expense.setCategory(request.getCategory() != null ? request.getCategory() : ExpenseCategory.OTHER);
        expense.setSplitType(splitType);
        expense.setDate(request.getDate());
        expense.setNotes(request.getNotes());

        expense = expenseRepository.save(expense);

        // Delete old splits
        splitRepository.deleteByExpenseId(expenseId);

        // Determine who to split with
        List<UUID> splitUserIds;
        if (request.getSplitWith() != null && !request.getSplitWith().isEmpty()) {
            splitUserIds = request.getSplitWith();
        } else {
            splitUserIds = tripMemberRepository.findByTripId(tripId).stream()
                    .filter(m -> m.getMemberStatus() == MemberStatus.APPROVED)
                    .map(TripMember::getUserId)
                    .toList();
        }

        // Create new splits
        List<ExpenseSplit> splits = new ArrayList<>();
        if (splitType == SplitType.CUSTOM && request.getCustomAmounts() != null) {
            for (Map.Entry<UUID, BigDecimal> entry : request.getCustomAmounts().entrySet()) {
                splits.add(ExpenseSplit.builder()
                        .expenseId(expense.getId())
                        .userId(entry.getKey())
                        .amount(entry.getValue())
                        .build());
            }
        } else {
            BigDecimal perPerson = request.getAmount()
                    .divide(BigDecimal.valueOf(splitUserIds.size()), 2, RoundingMode.HALF_UP);
            BigDecimal remainder = request.getAmount()
                    .subtract(perPerson.multiply(BigDecimal.valueOf(splitUserIds.size())));

            for (int i = 0; i < splitUserIds.size(); i++) {
                BigDecimal splitAmount = i == 0 ? perPerson.add(remainder) : perPerson;
                splits.add(ExpenseSplit.builder()
                        .expenseId(expense.getId())
                        .userId(splitUserIds.get(i))
                        .amount(splitAmount)
                        .build());
            }
        }

        splits = splitRepository.saveAll(splits);
        log.info("Expense '{}' updated in trip {} by user {}", expense.getTitle(), tripId, userId);

        User payer = userRepository.findById(expense.getPaidBy()).orElse(null);
        Map<UUID, User> usersById = userRepository.findAllById(
                splits.stream().map(ExpenseSplit::getUserId).toList()
        ).stream().collect(Collectors.toMap(User::getId, u -> u));

        List<ExpenseDTO.SplitDTO> splitDTOs = splits.stream().map(s -> {
            User u = usersById.get(s.getUserId());
            return ExpenseDTO.SplitDTO.builder()
                    .id(s.getId())
                    .userId(s.getUserId())
                    .userName(u != null ? u.getFirstName() + " " + u.getLastName() : null)
                    .amount(s.getAmount())
                    .build();
        }).toList();

        return ExpenseDTO.builder()
                .id(expense.getId())
                .tripId(expense.getTripId())
                .paidBy(expense.getPaidBy())
                .paidByName(payer != null ? payer.getFirstName() + " " + payer.getLastName() : null)
                .title(expense.getTitle())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .category(expense.getCategory())
                .splitType(expense.getSplitType())
                .date(expense.getDate())
                .notes(expense.getNotes())
                .splits(splitDTOs)
                .createdAt(expense.getCreatedAt())
                .build();
    }

    @Transactional
    public void deleteExpense(UUID userId, UUID tripId, UUID expenseId) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));
        validateMembership(userId, tripId);

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found"));

        if (!expense.getTripId().equals(tripId)) {
            throw new ExpenseAccessDeniedException("Expense does not belong to this trip");
        }

        if (!expense.getPaidBy().equals(userId)) {
            // Check if user is trip creator
            boolean isCreator = tripRepository.findById(tripId)
                    .map(t -> t.getCreatorId().equals(userId))
                    .orElse(false);
            if (!isCreator) {
                throw new ExpenseAccessDeniedException("Only the payer or trip creator can delete this expense");
            }
        }

        splitRepository.deleteByExpenseId(expenseId);
        expenseRepository.delete(expense);
        log.info("Expense {} deleted from trip {} by user {}", expenseId, tripId, userId);
    }

    /**
     * Calculate net balances for every member.
     * Positive balance = is owed money. Negative balance = owes money.
     */
    @Transactional(readOnly = true)
    public ExpenseSummaryDTO getSummary(UUID userId, UUID tripId) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));
        validateMembership(userId, tripId);

        List<Expense> expenses = expenseRepository.findByTripIdOrderByDateDesc(tripId);
        if (expenses.isEmpty()) {
            return ExpenseSummaryDTO.builder()
                    .totalSpent(BigDecimal.ZERO)
                    .expenseCount(0)
                    .categoryBreakdown(Map.of())
                    .memberSummaries(List.of())
                    .build();
        }

        List<UUID> expenseIds = expenses.stream().map(Expense::getId).toList();
        List<ExpenseSplit> allSplits = splitRepository.findByExpenseIdIn(expenseIds);

        // Total spent
        BigDecimal totalSpent = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Category breakdown
        Map<String, BigDecimal> categoryBreakdown = expenses.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getCategory().name(),
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));

        // Per-member: totalPaid and totalOwed
        Map<UUID, BigDecimal> paidByUser = new HashMap<>();
        Map<UUID, BigDecimal> owedByUser = new HashMap<>();

        for (Expense expense : expenses) {
            paidByUser.merge(expense.getPaidBy(), expense.getAmount(), BigDecimal::add);
        }
        for (ExpenseSplit split : allSplits) {
            owedByUser.merge(split.getUserId(), split.getAmount(), BigDecimal::add);
        }

        // Incorporate confirmed settlements
        List<TripSettlement> confirmedSettlements = settlementRepository.findByTripIdAndStatus(tripId, SettlementStatus.CONFIRMED);
        for (TripSettlement settlement : confirmedSettlements) {
            paidByUser.merge(settlement.getFromUserId(), settlement.getAmount(), BigDecimal::add);
            paidByUser.merge(settlement.getToUserId(), settlement.getAmount().negate(), BigDecimal::add);
        }

        Set<UUID> allUserIds = new HashSet<>();
        allUserIds.addAll(paidByUser.keySet());
        allUserIds.addAll(owedByUser.keySet());
        confirmedSettlements.forEach(s -> {
            allUserIds.add(s.getFromUserId());
            allUserIds.add(s.getToUserId());
        });

        Map<UUID, User> usersById = userRepository.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ExpenseSummaryDTO.MemberSummary> memberSummaries = allUserIds.stream().map(uid -> {
            BigDecimal paid = paidByUser.getOrDefault(uid, BigDecimal.ZERO);
            BigDecimal owed = owedByUser.getOrDefault(uid, BigDecimal.ZERO);
            User u = usersById.get(uid);
            return ExpenseSummaryDTO.MemberSummary.builder()
                    .userId(uid)
                    .userName(u != null ? u.getFirstName() + " " + u.getLastName() : null)
                    .totalPaid(paid)
                    .totalOwed(owed)
                    .netBalance(paid.subtract(owed))
                    .build();
        }).sorted(Comparator.comparing(ExpenseSummaryDTO.MemberSummary::getNetBalance).reversed())
                .toList();

        return ExpenseSummaryDTO.builder()
                .totalSpent(totalSpent)
                .expenseCount(expenses.size())
                .categoryBreakdown(categoryBreakdown)
                .memberSummaries(memberSummaries)
                .build();
    }

    @Transactional
    public TripSettlementDTO initiateSettlement(UUID userId, UUID tripId, CreateSettlementRequest request) {
        tripRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException("Trip not found"));
        validateMembership(userId, tripId);
        validateMembership(request.getToUserId(), tripId);

        SettlementStatus initialStatus = request.isDirectReceipt() ? SettlementStatus.CONFIRMED : SettlementStatus.PENDING;

        TripSettlement settlement = TripSettlement.builder()
                .tripId(tripId)
                .fromUserId(userId)
                .toUserId(request.getToUserId())
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.CASH)
                .status(initialStatus)
                .notes(request.getNotes())
                .build();

        settlement = settlementRepository.save(settlement);

        User sender = userRepository.findById(userId).orElse(null);
        User receiver = userRepository.findById(request.getToUserId()).orElse(null);
        String senderName = sender != null ? sender.getFirstName() + " " + sender.getLastName() : "Member";
        String receiverName = receiver != null ? receiver.getFirstName() + " " + receiver.getLastName() : "Member";

        if (initialStatus == SettlementStatus.PENDING) {
            notificationService.notifyUser(
                    request.getToUserId(),
                    senderName + " marked ₹" + request.getAmount() + " as paid to you via " + settlement.getPaymentMethod() + ". Tap to confirm.",
                    NotificationType.SETTLEMENT_REQUEST,
                    tripId
            );
            
            // Post automated chat message to Trip Chat
            SendMessageRequest msgReq = new SendMessageRequest();
            msgReq.setContent("💸 " + senderName + " recorded a payment of ₹" + request.getAmount() + " to " + receiverName + " via " + settlement.getPaymentMethod() + " (Pending Confirmation).");
            chatService.sendMessage(userId, tripId, msgReq);
        } else {
            SendMessageRequest msgReq = new SendMessageRequest();
            msgReq.setContent("✅ " + senderName + " settled ₹" + request.getAmount() + " with " + receiverName + " via " + settlement.getPaymentMethod() + ".");
            chatService.sendMessage(userId, tripId, msgReq);
        }

        return mapSettlementToDTO(settlement, senderName, receiverName);
    }

    @Transactional
    public TripSettlementDTO confirmSettlement(UUID userId, UUID tripId, UUID settlementId) {
        TripSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ExpenseNotFoundException("Settlement record not found"));

        if (!settlement.getToUserId().equals(userId)) {
            throw new ExpenseAccessDeniedException("Only the payment recipient can confirm this settlement.");
        }

        settlement.setStatus(SettlementStatus.CONFIRMED);
        settlement = settlementRepository.save(settlement);

        User sender = userRepository.findById(settlement.getFromUserId()).orElse(null);
        User receiver = userRepository.findById(settlement.getToUserId()).orElse(null);
        String senderName = sender != null ? sender.getFirstName() + " " + sender.getLastName() : "Member";
        String receiverName = receiver != null ? receiver.getFirstName() + " " + receiver.getLastName() : "Member";

        notificationService.notifyUser(
                settlement.getFromUserId(),
                receiverName + " confirmed receiving your payment of ₹" + settlement.getAmount() + ".",
                NotificationType.SETTLEMENT_CONFIRMED,
                tripId
        );

        SendMessageRequest msgReq = new SendMessageRequest();
        msgReq.setContent("✅ " + receiverName + " confirmed receiving ₹" + settlement.getAmount() + " payment from " + senderName + ".");
        chatService.sendMessage(userId, tripId, msgReq);

        return mapSettlementToDTO(settlement, senderName, receiverName);
    }

    @Transactional
    public TripSettlementDTO rejectSettlement(UUID userId, UUID tripId, UUID settlementId, String reason) {
        TripSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ExpenseNotFoundException("Settlement record not found"));

        if (!settlement.getToUserId().equals(userId)) {
            throw new ExpenseAccessDeniedException("Only the payment recipient can reject this settlement.");
        }

        settlement.setStatus(SettlementStatus.REJECTED);
        settlement.setRejectionReason(reason);
        settlement = settlementRepository.save(settlement);

        User sender = userRepository.findById(settlement.getFromUserId()).orElse(null);
        User receiver = userRepository.findById(settlement.getToUserId()).orElse(null);
        String senderName = sender != null ? sender.getFirstName() + " " + sender.getLastName() : "Member";
        String receiverName = receiver != null ? receiver.getFirstName() + " " + receiver.getLastName() : "Member";

        notificationService.notifyUser(
                settlement.getFromUserId(),
                receiverName + " declined your settlement request of ₹" + settlement.getAmount() + (reason != null ? ": " + reason : ""),
                NotificationType.SETTLEMENT_REQUEST,
                tripId
        );

        return mapSettlementToDTO(settlement, senderName, receiverName);
    }

    @Transactional(readOnly = true)
    public List<TripSettlementDTO> getTripSettlements(UUID userId, UUID tripId) {
        validateMembership(userId, tripId);
        List<TripSettlement> settlements = settlementRepository.findByTripId(tripId);
        if (settlements.isEmpty()) return List.of();

        Set<UUID> userIds = new HashSet<>();
        settlements.forEach(s -> {
            userIds.add(s.getFromUserId());
            userIds.add(s.getToUserId());
        });

        Map<UUID, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return settlements.stream().map(s -> {
            User sender = usersById.get(s.getFromUserId());
            User receiver = usersById.get(s.getToUserId());
            String sName = sender != null ? sender.getFirstName() + " " + sender.getLastName() : null;
            String rName = receiver != null ? receiver.getFirstName() + " " + receiver.getLastName() : null;
            return mapSettlementToDTO(s, sName, rName);
        }).sorted(Comparator.comparing(TripSettlementDTO::getCreatedAt).reversed()).toList();
    }

    private TripSettlementDTO mapSettlementToDTO(TripSettlement s, String fromUserName, String toUserName) {
        return TripSettlementDTO.builder()
                .id(s.getId())
                .tripId(s.getTripId())
                .fromUserId(s.getFromUserId())
                .fromUserName(fromUserName)
                .toUserId(s.getToUserId())
                .toUserName(toUserName)
                .amount(s.getAmount())
                .paymentMethod(s.getPaymentMethod())
                .status(s.getStatus())
                .notes(s.getNotes())
                .rejectionReason(s.getRejectionReason())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    /**
     * Optimize settlements using a greedy algorithm to minimize the number of transactions.
     * Uses the "min-cashflow" approach: repeatedly settle the max creditor with the max debtor.
     */
    @Transactional(readOnly = true)
    public List<SettlementDTO> getSettlements(UUID userId, UUID tripId) {
        ExpenseSummaryDTO summary = getSummary(userId, tripId);
        if (summary.getMemberSummaries().isEmpty()) return List.of();

        // Build mutable balance list
        List<Map.Entry<UUID, BigDecimal>> balances = summary.getMemberSummaries().stream()
                .map(m -> new AbstractMap.SimpleEntry<>(m.getUserId(), m.getNetBalance()))
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) != 0)
                .collect(Collectors.toList());

        Map<UUID, String> nameMap = summary.getMemberSummaries().stream()
                .collect(Collectors.toMap(
                        ExpenseSummaryDTO.MemberSummary::getUserId,
                        ExpenseSummaryDTO.MemberSummary::getUserName
                ));

        List<SettlementDTO> settlements = new ArrayList<>();

        while (true) {
            // Find max creditor (positive balance) and max debtor (negative balance)
            Map.Entry<UUID, BigDecimal> maxCreditor = null;
            Map.Entry<UUID, BigDecimal> maxDebtor = null;

            for (Map.Entry<UUID, BigDecimal> entry : balances) {
                if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                    if (maxCreditor == null || entry.getValue().compareTo(maxCreditor.getValue()) > 0) {
                        maxCreditor = entry;
                    }
                }
                if (entry.getValue().compareTo(BigDecimal.ZERO) < 0) {
                    if (maxDebtor == null || entry.getValue().compareTo(maxDebtor.getValue()) < 0) {
                        maxDebtor = entry;
                    }
                }
            }

            if (maxCreditor == null || maxDebtor == null) break;

            BigDecimal settleAmount = maxCreditor.getValue().min(maxDebtor.getValue().negate());

            settlements.add(SettlementDTO.builder()
                    .fromUserId(maxDebtor.getKey())
                    .fromUserName(nameMap.get(maxDebtor.getKey()))
                    .toUserId(maxCreditor.getKey())
                    .toUserName(nameMap.get(maxCreditor.getKey()))
                    .amount(settleAmount)
                    .build());

            // Update balances
            maxCreditor.setValue(maxCreditor.getValue().subtract(settleAmount));
            maxDebtor.setValue(maxDebtor.getValue().add(settleAmount));

            // Remove zeroed-out entries
            balances.removeIf(e -> e.getValue().abs().compareTo(new BigDecimal("0.01")) < 0);
        }

        return settlements;
    }

    private void validateMembership(UUID userId, UUID tripId) {
        boolean isMember = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .filter(m -> m.getMemberStatus() == MemberStatus.APPROVED)
                .isPresent();
        if (!isMember) {
            throw new ExpenseAccessDeniedException("You are not an approved member of this trip");
        }
    }
}
