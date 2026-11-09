// ReservationReleaseShortageTest.java
package ru.warehouse.inventory.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.warehouse.common.event.OutboxPublisher;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.inventory.dto.OperationDtos.ReleaseRequest;
import ru.warehouse.inventory.dto.OperationDtos.ShortageMark;
import ru.warehouse.inventory.entity.Reservation;
import ru.warehouse.inventory.entity.Stock;
import ru.warehouse.inventory.repository.ReservationRepository;
import ru.warehouse.inventory.repository.StockRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B1: при снятии резерва недобор регистрируется как недостача, остальное просто освобождается. */
@ExtendWith(MockitoExtension.class)
class ReservationReleaseShortageTest {

    @Mock StockRepository stockRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock OutboxPublisher outbox;
    @Mock ShortageService shortageService;
    @InjectMocks ReservationService service;

    final UUID requestId = UUID.randomUUID();
    final UUID actor = UUID.randomUUID();
    Reservation r1;
    Reservation r2;
    Stock s1;
    Stock s2;

    private void givenTwoActiveReservations() {
        s1 = stock(new BigDecimal("5"));
        s2 = stock(new BigDecimal("3"));
        r1 = reservation(s1, new BigDecimal("5"));
        r2 = reservation(s2, new BigDecimal("3"));
        when(reservationRepository.lockByRequestAndStatus(requestId, Reservation.Status.ACTIVE))
                .thenReturn(List.of(r1, r2));
        when(stockRepository.lockAll(any())).thenReturn(Map.of(s1.getId(), s1, s2.getId(), s2));
    }

    @Test
    void shortageIsRegisteredOnlyForMarkedReservation() {
        givenTwoActiveReservations();
        service.release(new ReleaseRequest(requestId, null,
                List.of(new ShortageMark(r1.getId(), new BigDecimal("2")))), actor);

        verify(shortageService).registerOnRelease(eq(r1), eq(s1),
                argThat(q -> q.compareTo(new BigDecimal("2")) == 0), eq(actor));
        verify(shortageService, never()).registerOnRelease(eq(r2), any(), any(), any());
        assertThat(r1.getStatus()).isEqualTo(Reservation.Status.RELEASED);
        assertThat(r2.getStatus()).isEqualTo(Reservation.Status.RELEASED);
        assertThat(s1.getReservedQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void releaseWithoutMarksRegistersNothing() {
        givenTwoActiveReservations();
        service.release(new ReleaseRequest(requestId, null), actor);
        verify(shortageService, never()).registerOnRelease(any(), any(), any(), any());
    }

    @Test
    void shortageLargerThanReservationIsRejected() {
        givenTwoActiveReservations();
        ReleaseRequest req = new ReleaseRequest(requestId, null,
                List.of(new ShortageMark(r2.getId(), new BigDecimal("4"))));
        assertThatThrownBy(() -> service.release(req, actor)).isInstanceOf(ApiException.class);
    }

    @Test
    void repeatedReleaseIgnoresMarks() {
        when(reservationRepository.lockByRequestAndStatus(requestId, Reservation.Status.ACTIVE)).thenReturn(List.of());
        when(stockRepository.lockAll(any())).thenReturn(Map.of());
        service.release(new ReleaseRequest(requestId, null,
                List.of(new ShortageMark(UUID.randomUUID(), BigDecimal.ONE))), actor);
        verify(shortageService, never()).registerOnRelease(any(), any(), any(), any());
    }

    private static Stock stock(BigDecimal reserved) {
        Stock s = new Stock();
        s.setId(UUID.randomUUID());
        s.setQuantity(new BigDecimal("10"));
        s.setReservedQuantity(reserved);
        return s;
    }

    private Reservation reservation(Stock stock, BigDecimal qty) {
        Reservation r = new Reservation();
        r.setId(UUID.randomUUID());
        r.setRequestId(requestId);
        r.setRequestItemId(UUID.randomUUID());
        r.setStock(stock);
        r.setQuantity(qty);
        return r;
    }
}
