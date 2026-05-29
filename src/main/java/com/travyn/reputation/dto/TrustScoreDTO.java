package com.travyn.reputation.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TrustScoreDTO {
    private String id;
    private String userId;
    private Integer totalScore;
    private Integer baseScore;
    private Integer govIdScore;
    private Integer profileScore;
    private Integer reviewScore;
    private LocalDateTime lastComputedAt;
}
