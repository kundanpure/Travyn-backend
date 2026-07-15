package com.travyn.destination.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "destination_insights")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DestinationInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String destination;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "author_name", nullable = false, length = 100)
    private String authorName;

    @Column(name = "author_avatar_url")
    private String authorAvatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InsightCategory category;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    @Builder.Default
    private Integer upvotes = 0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "insight_upvotes", joinColumns = @JoinColumn(name = "insight_id"))
    @Column(name = "user_id")
    @Builder.Default
    private java.util.Set<UUID> upvotedBy = new java.util.HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
