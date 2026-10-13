package com.tripbudget.tripbudget_core.trip.dtos.request;

import com.tripbudget.tripbudget_core.trip.enums.TripStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateTripRequest {

    @NotBlank(message = "Trip name is required")
    @Size(max = 200)
    private String name;

    @NotBlank(message = "Destination is required")
    @Size(max = 255)
    private String destination;

    @Size(max = 5000)
    private String description;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @Pattern(
            regexp = "^[A-Z]{3}$",
            message = "Base currency must be a 3-letter uppercase code"
    )
    private String baseCurrency;

    private TripStatus status;

    private java.util.UUID coverUploadId;

    private boolean removeCover;
}

