package ru.mephi.stay.hotel.catalog;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface SlotRepository extends JpaRepository<RoomSlot, UUID> {
    @Query("""
            select s from RoomSlot s where s.released = false and s.startDate < :end and s.endDate > :start
              and (:hotelId is null or s.room.hotel.id = :hotelId)
            """)
    List<RoomSlot> overlapping(Long hotelId, LocalDate start, LocalDate end);

    boolean existsByRoomIdAndReleasedFalseAndStartDateLessThanAndEndDateGreaterThan(
            long roomId, LocalDate end, LocalDate start);

    boolean existsByRoomIdAndReleasedFalseAndEndDateGreaterThan(long roomId, LocalDate today);

    @Modifying
    @Query("delete from RoomSlot s where s.room.id = :roomId")
    void deleteForRoom(@Param("roomId") long roomId);
}
