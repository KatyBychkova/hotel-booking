package ru.mephi.stay.common;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record SlotCommand(@NotNull UUID bookingId, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
