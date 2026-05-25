package com.travyn.itinerary.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReorderItemsRequest {

    @NotEmpty(message = "Item IDs list cannot be empty")
    private List<UUID> itemIds;
}
