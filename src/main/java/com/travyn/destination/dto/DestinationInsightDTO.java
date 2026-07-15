package com.travyn.destination.dto;

import com.travyn.destination.entity.InsightCategory;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class DestinationInsightDTO {
    private UUID id;
    private String destination;
    private UUID authorId;
    private String authorName;
    private String authorAvatarUrl;
    private InsightCategory category;
    private String content;
    private Integer upvotes;
    private boolean isUpvotedByMe;
    private Instant createdAt;
}
