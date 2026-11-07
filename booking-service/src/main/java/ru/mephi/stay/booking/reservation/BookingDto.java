package ru.mephi.stay.booking.reservation;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import ru.mephi.stay.common.SlotCommand;

public final class BookingDto {
    private BookingDto() {}

    public record Create(@NotNull UUID requestId, @NotNull Boolean autoSelect, Long roomId,
                         @Positive Long hotelId, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
    public record View(UUID id, UUID requestId, Long roomId, LocalDate startDate, LocalDate endDate,
                       BookingStatus status, Instant createdAt) {
        SlotCommand slot() { return new SlotCommand(id, startDate, endDate); }
    }
    public record History(List<View> items, int page, int size, long total) {}
}
