package ru.mephi.stay.booking.hotel;

import java.time.LocalDate;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import ru.mephi.stay.booking.account.TokenService;
import ru.mephi.stay.common.*;

@Component
class HttpHotelClient implements HotelClient {
    private final RestClient client;
    private final HotelCalls calls;
    private final TokenService tokens;

    HttpHotelClient(RestClient.Builder builder, HotelCalls calls, TokenService tokens, @Value("${app.hotel-url}") String url) {
        this.client = builder.baseUrl(url).build();
        this.calls = calls;
        this.tokens = tokens;
    }

    @Override
    public List<RoomView> recommend(Long hotelId, LocalDate start, LocalDate end) {
        return calls.request("recommend", false, () -> {
            var rooms = client.get().uri(u -> u.path("/api/rooms/recommend")
                            .queryParamIfPresent("hotelId", Optional.ofNullable(hotelId))
                            .queryParam("startDate", start).queryParam("endDate", end).build())
                    .headers(this::headers).retrieve().body(RoomView[].class);
            if (rooms == null) throw new RestClientException("Пустой ответ со списком номеров");
            return List.of(rooms);
        });
    }

    @Override
    public void confirm(long roomId, SlotCommand slot) { send(roomId, slot, "confirm-availability", false); }

    @Override
    public void release(long roomId, SlotCommand slot) { send(roomId, slot, "release", true); }

    private void send(long roomId, SlotCommand slot, String operation, boolean release) {
        calls.request(operation, release, () -> client.post().uri("/api/rooms/{id}/{action}", roomId, operation)
                .headers(this::headers).body(slot).retrieve().toBodilessEntity());
    }

    private void headers(HttpHeaders headers) {
        headers.setBearerAuth(tokens.forHotel());
        headers.set(TraceIds.HEADER, TraceIds.from(MDC.get("traceId")));
    }
}
