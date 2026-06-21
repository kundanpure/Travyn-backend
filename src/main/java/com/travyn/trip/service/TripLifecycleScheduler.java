package com.travyn.trip.service;

import com.travyn.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripLifecycleScheduler {

    private final TripRepository tripRepository;

    /**
     * Runs daily at midnight to auto-transition trip statuses:
     * - OPEN/FULL → IN_PROGRESS when today is within [startDate, endDate]
     * - OPEN/FULL/IN_PROGRESS → COMPLETED when endDate has passed
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void updateTripStatuses() {
        LocalDate today = LocalDate.now();

        // 1. Trips that have started → IN_PROGRESS
        int started = tripRepository.startTrips(today);
        if (started > 0) {
            log.info("🚀 Transitioned {} trip(s) to IN_PROGRESS", started);
        }

        // 2. Trips that have ended → COMPLETED
        int completed = tripRepository.completeTrips(today);
        if (completed > 0) {
            log.info("✅ Transitioned {} trip(s) to COMPLETED", completed);
        }
    }
}
