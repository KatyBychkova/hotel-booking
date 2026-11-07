package ru.mephi.stay.hotel.catalog;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.mephi.stay.common.*;
import static ru.mephi.stay.hotel.catalog.CatalogDto.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class CatalogService {
    private final HotelRepository hotels;
    private final RoomRepository rooms;
    private final SlotRepository slots;

    List<HotelView> hotels() {
        return hotels.findAll(Sort.by("id")).stream().map(HotelView::from).toList();
    }

    HotelView hotel(long id) { return HotelView.from(findHotel(id)); }

    @Transactional
    HotelView createHotel(HotelInput input) {
        return HotelView.from(hotels.save(new Hotel(input)));
    }

    @Transactional
    HotelView updateHotel(long id, HotelInput input) {
        Hotel hotel = findHotel(id);
        hotel.update(input);
        return HotelView.from(hotel);
    }

    @Transactional
    void deleteHotel(long id) {
        if (rooms.existsByHotelId(id)) throw conflict("Сначала удалите номера отеля");
        hotels.delete(findHotel(id));
    }

    RoomView room(long id) {
        return rooms.findById(id).orElseThrow(() -> missing("Номер")).view();
    }

    @Transactional
    RoomView createRoom(RoomInput input) {
        return rooms.save(new Room(findHotel(input.hotelId()), input)).view();
    }

    @Transactional
    RoomView updateRoom(long id, RoomInput input) {
        Room room = lockRoom(id);
        if (!input.hotelId().equals(room.getHotel().getId())) throw conflict("Номер уже закреплён за другим отелем");
        room.update(input);
        return room.view();
    }

    @Transactional
    void deleteRoom(long id) {
        Room room = lockRoom(id);
        if (slots.existsByRoomIdAndReleasedFalseAndEndDateGreaterThan(id, LocalDate.now())) {
            throw conflict("У номера есть действующие бронирования");
        }
        slots.deleteForRoom(id);
        rooms.delete(room);
    }

    List<RoomView> free(Long hotelId, LocalDate start, LocalDate end, boolean recommend) {
        if (start == null && end == null) {
            start = LocalDate.now();
            end = start.plusDays(1);
        }
        Dates.check(start, end);
        var sort = recommend ? Sort.by("timesBooked", "id") : Sort.unsorted();
        return rooms.free(hotelId, start, end, sort).stream().map(Room::view).toList();
    }

    List<RoomStats> stats(Long hotelId, LocalDate start, LocalDate end) {
        Dates.check(start, end);
        long days = ChronoUnit.DAYS.between(start, end);
        var byRoom = slots.overlapping(hotelId, start, end).stream()
                .collect(Collectors.groupingBy(s -> s.getRoom().getId()));
        return rooms.forStats(hotelId).stream().map(room -> {
            var stays = byRoom.getOrDefault(room.getId(), List.of());
            long nights = stays.stream().mapToLong(s -> ChronoUnit.DAYS.between(
                    s.getStartDate().isBefore(start) ? start : s.getStartDate(),
                    s.getEndDate().isAfter(end) ? end : s.getEndDate())).sum();
            return new RoomStats(room.getId(), room.getHotel().getId(), room.getNumber(), room.getTimesBooked(),
                    stays.size(), nights, Math.round(nights * 10000.0 / days) / 100.0);
        }).toList();
    }

    private Room lockRoom(long id) { return rooms.lock(id).orElseThrow(() -> missing("Номер")); }
    private Hotel findHotel(long id) { return hotels.findById(id).orElseThrow(() -> missing("Отель")); }
    private ResponseStatusException missing(String type) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " не найден");
    }
    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
