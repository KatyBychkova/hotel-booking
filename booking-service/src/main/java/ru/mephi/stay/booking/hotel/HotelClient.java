package ru.mephi.stay.booking.hotel;

import java.time.LocalDate;
import java.util.List;
import ru.mephi.stay.common.*;

public interface HotelClient {
    List<RoomView> recommend(Long hotelId, LocalDate start, LocalDate end);
    void confirm(long roomId, SlotCommand slot);
    void release(long roomId, SlotCommand slot);
}
