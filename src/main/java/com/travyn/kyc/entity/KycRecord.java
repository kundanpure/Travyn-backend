package com.travyn.kyc.entity;

import com.travyn.auth.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "kyc_records")
public class KycRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "aadhaar_last4", length = 4, nullable = false)
    private String aadhaarLast4;

    @Column(name = "verified_name", nullable = false)
    private String verifiedName;

    @Column(nullable = false)
    private String dob;

    @Column(nullable = false)
    private String gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private KycStatus status;

    @Column(name = "verified_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant verifiedAt;
}
