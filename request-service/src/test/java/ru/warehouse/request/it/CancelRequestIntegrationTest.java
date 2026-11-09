package ru.warehouse.request.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.request.client.InventoryClient;
import ru.warehouse.request.client.InventoryContract.ShortageMark;
import ru.warehouse.request.entity.RequestStatus;
import ru.warehouse.request.service.Actor;
import ru.warehouse.request.service.RequestService;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Отмена заявки в каждом статусе (R15, B1, B5, B6): что разрешено кладовщику и владельцу, что отклоняется, снимается ли
 * резерв в inventory, передаётся ли недобор, отклоняются ли открытые задания, пишется ли история.
 */
class CancelRequestIntegrationTest extends IntegrationTestBase {
    @MockitoBean
    InventoryClient inventory;
    @Autowired
    RequestService requestService;

    private static final Set<RequestStatus> STOREKEEPER_MAY_CANCEL = EnumSet.of(RequestStatus.SUBMITTED,
            RequestStatus.APPROVED, RequestStatus.PARTIALLY_AVAILABLE, RequestStatus.RESERVED,
            RequestStatus.RESERVED_PARTIAL, RequestStatus.PICKING, RequestStatus.PICKING_WITH_SHORTAGE,
            RequestStatus.READY_FOR_ISSUE);
    private static final Set<RequestStatus> OWNER_MAY_CANCEL = EnumSet.of(RequestStatus.SUBMITTED,
            RequestStatus.APPROVED, RequestStatus.PARTIALLY_AVAILABLE);
    /** Статусы, в которых резерв мог остаться или существует: при отмене вызывается снятие резерва в inventory. */
    private static final Set<RequestStatus> RELEASES_RESERVATION = EnumSet.of(RequestStatus.APPROVED,
            RequestStatus.PARTIALLY_AVAILABLE, RequestStatus.RESERVED, RequestStatus.RESERVED_PARTIAL,
            RequestStatus.PICKING, RequestStatus.PICKING_WITH_SHORTAGE, RequestStatus.READY_FOR_ISSUE);

    private record Fixture(UUID requestId, UUID itemId, UUID owner, UUID reservationId) {}

    private static boolean reservedLike(RequestStatus s) {
        return s == RequestStatus.RESERVED || s == RequestStatus.RESERVED_PARTIAL || s == RequestStatus.PICKING
                || s == RequestStatus.PICKING_WITH_SHORTAGE || s == RequestStatus.PICKED
                || s == RequestStatus.READY_FOR_ISSUE;
    }

    /** Заявка в нужном статусе; для сбора есть задание (при недоборе с одной строкой SHORTAGE: назначено 5, собрано 2). */
    private Fixture create(RequestStatus status, boolean pickedSomething) {
        UUID request = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        BigDecimal reserved = reservedLike(status) ? new BigDecimal("5") : BigDecimal.ZERO;
        jdbc.update("INSERT INTO requests (id, number, requester_id, warehouse_id, status) VALUES (?, ?, ?, ?, ?)",
                request, "RQ-T-" + request, owner, UUID.randomUUID(), status.name());
        jdbc.update("INSERT INTO request_items (id, request_id, line_no, product_id, product_sku, product_name, unit_code, "
                        + "requested_quantity, approved_quantity, reserved_quantity) VALUES (?, ?, 1, ?, 'SKU', 'name', 'PCS', 5, 5, ?)",
                item, request, UUID.randomUUID(), reserved);
        boolean hasTask = status == RequestStatus.PICKING || status == RequestStatus.PICKING_WITH_SHORTAGE
                || status == RequestStatus.PICKED || status == RequestStatus.READY_FOR_ISSUE;
        if (hasTask) {
            boolean open = status == RequestStatus.PICKING || status == RequestStatus.PICKING_WITH_SHORTAGE;
            UUID task = UUID.randomUUID();
            jdbc.update("INSERT INTO picking_tasks (id, number, request_id, status, created_by) VALUES (?, ?, ?, ?, ?)",
                    task, "PT-T-" + task, request, open ? "IN_PROGRESS" : "COMPLETED", UUID.randomUUID());
            String rowStatus = pickedSomething ? "PICKED" : (status == RequestStatus.PICKING ? "PENDING" : "SHORTAGE");
            BigDecimal picked = pickedSomething ? new BigDecimal("5")
                    : (status == RequestStatus.PICKING_WITH_SHORTAGE ? new BigDecimal("2")
                    : status == RequestStatus.PICKING ? null : BigDecimal.ZERO);
            jdbc.update("INSERT INTO picking_items (id, task_id, request_item_id, reservation_id, stock_id, section_id, "
                            + "section_code, requested_quantity, picked_quantity, status) VALUES (?, ?, ?, ?, ?, ?, 'A-1', 5, ?, ?)",
                    UUID.randomUUID(), task, item, reservation, UUID.randomUUID(), UUID.randomUUID(), picked, rowStatus);
        }
        return new Fixture(request, item, owner, reservation);
    }

    private String statusOf(UUID request) {
        return jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, request);
    }

    @ParameterizedTest
    @EnumSource(RequestStatus.class)
    void storekeeper_cancelsOnlyAllowedStatuses(RequestStatus status) {
        Fixture f = create(status, false);
        Actor storekeeper = new Actor(UUID.randomUUID(), true, false);
        if (STOREKEEPER_MAY_CANCEL.contains(status)) {
            requestService.cancel(f.requestId(), "причина", storekeeper);
            assertThat(statusOf(f.requestId())).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM request_status_history WHERE request_id = ? AND to_status = 'CANCELLED'",
                    Integer.class, f.requestId())).as("история статусов").isEqualTo(1);
            if (RELEASES_RESERVATION.contains(status)) {
                verify(inventory).release(eq(f.requestId()), anyList());
            } else {
                verify(inventory, never()).release(any(UUID.class), anyList());
            }
        } else {
            assertThatThrownBy(() -> requestService.cancel(f.requestId(), "причина", storekeeper))
                    .isInstanceOf(ApiException.class).hasMessageContaining("Отмена недоступна");
            assertThat(statusOf(f.requestId())).as("статус не изменился").isEqualTo(status.name());
            verify(inventory, never()).release(any(UUID.class), anyList());
        }
    }

    @ParameterizedTest
    @EnumSource(RequestStatus.class)
    void owner_cancelsOnlyBeforeReservation(RequestStatus status) {
        Fixture f = create(status, false);
        Actor owner = new Actor(f.owner(), false, false);
        if (OWNER_MAY_CANCEL.contains(status)) {
            requestService.cancel(f.requestId(), "передумал", owner);
            assertThat(statusOf(f.requestId())).isEqualTo("CANCELLED");
        } else {
            assertThatThrownBy(() -> requestService.cancel(f.requestId(), "передумал", owner))
                    .isInstanceOf(ApiException.class).hasMessageContaining("Отмена недоступна");
            assertThat(statusOf(f.requestId())).isEqualTo(status.name());
        }
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"SUBMITTED", "APPROVED", "RESERVED", "PICKING"})
    void foreignUser_getsNotFound_andNothingHappens(RequestStatus status) {
        Fixture f = create(status, false);
        Actor stranger = new Actor(UUID.randomUUID(), false, false);
        assertThatThrownBy(() -> requestService.cancel(f.requestId(), "чужая", stranger))
                .isInstanceOf(ApiException.class).hasMessageContaining("не найдена");
        assertThat(statusOf(f.requestId())).isEqualTo(status.name());
        verify(inventory, never()).release(any(UUID.class), anyList());
    }

    @Test
    void cancelFromPickingWithShortage_passesShortageMarkAndRejectsOpenTask() {
        Fixture f = create(RequestStatus.PICKING_WITH_SHORTAGE, false);
        requestService.cancel(f.requestId(), "не пришли", new Actor(UUID.randomUUID(), true, false));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShortageMark>> marks = ArgumentCaptor.forClass(List.class);
        verify(inventory).release(eq(f.requestId()), marks.capture());
        assertThat(marks.getValue()).hasSize(1);
        assertThat(marks.getValue().get(0).reservationId()).isEqualTo(f.reservationId());
        assertThat(marks.getValue().get(0).quantity()).isEqualByComparingTo("3"); // назначено 5, собрано 2
        assertThat(jdbc.queryForObject("SELECT status FROM picking_tasks WHERE request_id = ?", String.class, f.requestId()))
                .isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM request_items WHERE id = ?", BigDecimal.class, f.itemId()))
                .isEqualByComparingTo("0");
    }

    @Test
    void readyForIssueWithNothingPicked_isCancelledAndReleasesReservation() {
        Fixture f = create(RequestStatus.READY_FOR_ISSUE, false);
        requestService.cancel(f.requestId(), "получатель не пришёл", new Actor(UUID.randomUUID(), true, false));
        assertThat(statusOf(f.requestId())).isEqualTo("CANCELLED");
        verify(inventory).release(eq(f.requestId()), anyList());
    }

    @Test
    void readyForIssueWithPickedGoods_cannotBeCancelled() {
        Fixture f = create(RequestStatus.READY_FOR_ISSUE, true);
        assertThatThrownBy(() -> requestService.cancel(f.requestId(), "хочу", new Actor(UUID.randomUUID(), true, false)))
                .isInstanceOf(ApiException.class).hasMessageContaining("уже собрана");
        assertThat(statusOf(f.requestId())).isEqualTo("READY_FOR_ISSUE");
        verify(inventory, never()).release(any(UUID.class), anyList());
    }

    @Test
    void inventoryFailure_leavesRequestUntouched_andLeaseIsReleasedForRetry() {
        Fixture f = create(RequestStatus.RESERVED, false);
        org.mockito.Mockito.when(inventory.release(eq(f.requestId()), anyList()))
                .thenThrow(new IllegalStateException("inventory недоступен"));
        Actor storekeeper = new Actor(UUID.randomUUID(), true, false);

        assertThatThrownBy(() -> requestService.cancel(f.requestId(), "причина", storekeeper))
                .isInstanceOf(IllegalStateException.class);
        assertThat(statusOf(f.requestId())).as("статус не изменился").isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("SELECT op_token FROM requests WHERE id = ?", UUID.class, f.requestId()))
                .as("аренда снята").isNull();

        org.mockito.Mockito.reset(inventory);
        requestService.cancel(f.requestId(), "причина", storekeeper); // повтор проходит
        assertThat(statusOf(f.requestId())).isEqualTo("CANCELLED");
    }
}
