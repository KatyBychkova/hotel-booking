package ru.mephi.stay.hotel.catalog;

import jakarta.validation.constraints.*;

public final class CatalogDto {
    private CatalogDto() {}

    public record HotelInput(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 240) String address) {}
    public record HotelView(long id, String name, String address) {
        static HotelView from(Hotel h) { return new HotelView(h.getId(), h.getName(), h.getAddress()); }
    }
    public record RoomInput(@NotNull @Positive Long hotelId, @NotBlank @Size(max = 30) String number,
                            @NotNull Boolean available) {}
    public record RoomStats(long roomId, long hotelId, String number, long timesBooked,
                            long bookingsInPeriod, long occupiedNights, double occupancyPercent) {}
}
