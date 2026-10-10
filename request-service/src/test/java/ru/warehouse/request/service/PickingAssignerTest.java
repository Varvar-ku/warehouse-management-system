// src/test/java/ru/warehouse/request/service/PickingAssignerTest.java
package ru.warehouse.request.service;

import org.junit.jupiter.api.Test;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.request.entity.PickingItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PickingAssignerTest {
    private final UUID worker = UUID.randomUUID();

    private static PickingItem row(String section, String quantity) {
        PickingItem i = new PickingItem();
        i.setSectionCode(section);
        i.setRequestedQuantity(new BigDecimal(quantity));
        return i;
    }

    @Test
    void wholeRow_goesToWorkerWithoutSplit() {
        PickingItem a = row("A", "10");
        List<PickingItem> created = PickingAssigner.assign(List.of(a), worker, new BigDecimal("10"));
        assertThat(created).isEmpty();
        assertThat(a.getAssignedWorkerId()).isEqualTo(worker);
    }

    @Test
    void partOfRow_splitsTheRow() {
        PickingItem a = row("A", "10");
        List<PickingItem> created = PickingAssigner.assign(List.of(a), worker, new BigDecimal("4"));
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getRequestedQuantity()).isEqualByComparingTo("4");
        assertThat(created.get(0).getAssignedWorkerId()).isEqualTo(worker);
        assertThat(a.getRequestedQuantity()).isEqualByComparingTo("6");   // остаток строки остаётся свободным
        assertThat(a.getAssignedWorkerId()).isNull();
    }

    @Test
    void quantityIsSpreadAcrossRows() {
        PickingItem a = row("A", "3");
        PickingItem b = row("B", "4");
        List<PickingItem> created = PickingAssigner.assign(List.of(a, b), worker, new BigDecimal("5"));
        assertThat(a.getAssignedWorkerId()).isEqualTo(worker);             // первая строка целиком
        assertThat(created).hasSize(1);                                    // вторая разбита: 2 работнику, 2 свободны
        assertThat(created.get(0).getRequestedQuantity()).isEqualByComparingTo("2");
        assertThat(b.getRequestedQuantity()).isEqualByComparingTo("2");
    }

    @Test
    void overAssignment_isRejected() {
        assertThatThrownBy(() -> PickingAssigner.assign(List.of(row("A", "3")), worker, new BigDecimal("4")))
                .isInstanceOf(ApiException.class);
    }
}
