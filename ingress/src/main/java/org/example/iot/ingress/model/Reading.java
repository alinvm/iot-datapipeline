package org.example.iot.ingress.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record Reading(
        @NotNull UUID deviceId,
        @NotNull @Positive long timestamp,  // ms since epoch
        @NotBlank String type,
        @NotBlank String unit,
        @NotNull double value
) {}
