package com.travyn.itinerary.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDayRequest {

    @NotNull(message = "Date is required")
    private LocalDate date;

    private String title;

    private String notes;
}
