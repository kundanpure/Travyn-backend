package com.travyn.reputation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReviewWindowDTO {
    private LocalDateTime windowOpens;
    private LocalDateTime windowCloses;
    private boolean windowOpen;
    private List<PeerReviewStatus> peers;

    @Data
    @Builder
    public static class PeerReviewStatus {
        private String peerId;
        private String peerName;
        private String profilePhotoUrl;
        private boolean iReviewedThem;
        private boolean theyReviewedMe;
        private boolean isPublished;
    }
}
