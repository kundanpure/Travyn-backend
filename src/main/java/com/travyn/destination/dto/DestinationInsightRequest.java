package com.travyn.destination.dto;

import com.travyn.destination.entity.InsightCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DestinationInsightRequest {
    @NotNull
    private InsightCategory category;

    @NotBlank
    private String content;
}
