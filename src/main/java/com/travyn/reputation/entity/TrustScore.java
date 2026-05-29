package com.travyn.reputation.entity;

import com.travyn.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trust_scores")
@Getter
@Setter
public class TrustScore {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private Integer totalScore = 0;

    @Column(nullable = false)
    private Integer baseScore = 0;

    @Column(nullable = false)
    private Integer govIdScore = 0;

    @Column(nullable = false)
    private Integer profileScore = 0;

    @Column(nullable = false)
    private Integer reviewScore = 0;

    @Column(nullable = false)
    private LocalDateTime lastComputedAt;
}
