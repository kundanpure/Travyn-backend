package com.travyn.kyc.repository;

import com.travyn.kyc.entity.KycRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface KycRecordRepository extends JpaRepository<KycRecord, UUID> {
    boolean existsByAadhaarLast4AndStatus(String aadhaarLast4, com.travyn.kyc.entity.KycStatus status);
}
