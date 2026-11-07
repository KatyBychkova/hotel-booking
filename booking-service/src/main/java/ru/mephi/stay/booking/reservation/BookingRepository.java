package ru.mephi.stay.booking.reservation;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByUserIdAndRequestId(long userId, UUID requestId);
    Optional<Booking> findByIdAndUserId(UUID id, long userId);
    Page<Booking> findByUserId(long userId, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lock(UUID id);

    @Query("select b.id from Booking b where b.releasePending = true order by b.createdAt")
    List<UUID> awaitingRelease();
}
