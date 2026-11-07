package ru.mephi.stay.common;

public record RoomView(long id, long hotelId, String number, boolean available, long timesBooked) {}
