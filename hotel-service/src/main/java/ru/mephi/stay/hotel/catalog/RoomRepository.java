package ru.mephi.stay.hotel.catalog;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.*;

interface RoomRepository extends JpaRepository<Room, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> lock(long id);

    @Query("""
            select r from Room r where r.available = true and (:hotelId is null or r.hotel.id = :hotelId)
              and not exists (select s.id from RoomSlot s where s.room = r and s.released = false
                              and s.startDate < :end and s.endDate > :start)
            """)
    List<Room> free(Long hotelId, LocalDate start, LocalDate end, Sort sort);

    @Query("select r from Room r where :hotelId is null or r.hotel.id = :hotelId order by r.id")
    List<Room> forStats(Long hotelId);

    boolean existsByHotelId(long hotelId);
}
