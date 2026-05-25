package com.travyn.itinerary.dto;

import com.travyn.itinerary.entity.ItemCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateItemRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    private String description;

    @Size(max = 300, message = "Location must be at most 300 characters")
    private String location;

    private LocalTime startTime;

    private LocalTime endTime;

    private ItemCategory category;
}
