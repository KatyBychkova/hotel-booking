package ru.mephi.stay.hotel.catalog;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.mephi.stay.common.*;

@Service
@RequiredArgsConstructor
@Transactional
class SlotService {
    private static final Logger log = LoggerFactory.getLogger(SlotService.class);
    private final RoomRepository rooms;
    private final SlotRepository slots;

    void confirm(long roomId, SlotCommand cmd) {
        Dates.check(cmd.startDate(), cmd.endDate());
        // Проверяем даты под блокировкой номера: два запроса не смогут занять один период.
        Room room = lock(roomId);
        var existing = slots.findById(cmd.bookingId());
        if (existing.isPresent()) {
            RoomSlot slot = existing.get();
            requireMatch(slot, roomId, cmd);
            if (slot.isReleased()) throw conflict("Бронирование уже отменено");
            return;
        }
        if (cmd.startDate().isBefore(LocalDate.now()) || !room.isAvailable()
                || slots.existsByRoomIdAndReleasedFalseAndStartDateLessThanAndEndDateGreaterThan(
                        roomId, cmd.endDate(), cmd.startDate())) {
            throw conflict("Номер недоступен на выбранные даты");
        }
        slots.save(new RoomSlot(room, cmd, false));
        room.reserved();
        event("slot.confirmed", roomId, cmd);
    }

    void release(long roomId, SlotCommand cmd) {
        Dates.check(cmd.startDate(), cmd.endDate());
        Room room = lock(roomId);
        var existing = slots.findById(cmd.bookingId());
        if (existing.isEmpty()) {
            // Ответ мог опоздать. Запоминаем отмену, даже если confirm ещё не дошёл.
            slots.save(new RoomSlot(room, cmd, true));
        } else {
            RoomSlot slot = existing.get();
            requireMatch(slot, roomId, cmd);
            if (!slot.isReleased()) {
                slot.release();
                room.released();
            }
        }
        event("slot.released", roomId, cmd);
    }

    private Room lock(long id) {
        return rooms.lock(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Номер не найден"));
    }

    private void requireMatch(RoomSlot slot, long id, SlotCommand cmd) {
        if (!slot.matches(id, cmd)) throw conflict("Идентификатор брони уже связан с другим номером или датами");
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private void event(String name, long id, SlotCommand cmd) {
        log.atInfo().addKeyValue("bookingId", cmd.bookingId()).addKeyValue("roomId", id).log(name);
    }
}
