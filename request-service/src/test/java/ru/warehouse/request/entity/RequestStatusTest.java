// src/test/java/ru/warehouse/request/entity/RequestStatusTest.java
package ru.warehouse.request.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static ru.warehouse.request.entity.RequestStatus.*;

class RequestStatusTest {

    @Test
    void happyPath_followsTheTzTable() {
        assertThat(DRAFT.canTransitionTo(SUBMITTED)).isTrue();
        assertThat(SUBMITTED.canTransitionTo(APPROVED)).isTrue();
        assertThat(APPROVED.canTransitionTo(RESERVED)).isTrue();
        assertThat(RESERVED.canTransitionTo(PICKING)).isTrue();
        assertThat(PICKING.canTransitionTo(PICKED)).isTrue();
        assertThat(READY_FOR_ISSUE.canTransitionTo(ISSUED)).isTrue();
        assertThat(ISSUED.canTransitionTo(CLOSED)).isTrue();
    }

    @Test
    void forbiddenTransitions_areRejected() {
        assertThat(DRAFT.canTransitionTo(APPROVED)).isFalse();
        assertThat(SUBMITTED.canTransitionTo(RESERVED)).isFalse();
        assertThat(PICKED.canTransitionTo(CANCELLED)).isFalse();   // собранное не отменяется (ТЗ)
        assertThat(ISSUED.canTransitionTo(CANCELLED)).isFalse();   // выдача окончательная
        assertThat(CLOSED.canTransitionTo(DRAFT)).isFalse();       // CLOSED конечный
        assertThat(REJECTED.canTransitionTo(RESERVED)).isFalse();
        assertThat(RESERVED.canTransitionTo(REJECTED)).isFalse();  // после резерва только отмена
    }

    @Test
    @SuppressWarnings("deprecation")
    void underReview_isGone_fromTheModel() {
        for (RequestStatus s : values()) {
            assertThat(s.canTransitionTo(UNDER_REVIEW)).isFalse();
            assertThat(UNDER_REVIEW.canTransitionTo(s)).isFalse();
        }
    }

    @Test
    void addedTransitions_areAllowed() {
        assertThat(APPROVED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(RESERVED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(SUBMITTED.canTransitionTo(PARTIALLY_AVAILABLE)).isTrue();
        assertThat(SUBMITTED.canTransitionTo(REJECTED)).isTrue();      // B6
        assertThat(APPROVED.canTransitionTo(REJECTED)).isTrue();       // B6
        assertThat(APPROVED.canTransitionTo(PARTIALLY_AVAILABLE)).isTrue(); // повторная проверка
        assertThat(PARTIALLY_AVAILABLE.canTransitionTo(APPROVED)).isTrue();
        // READY_FOR_ISSUE → CANCELLED только при нулевом сборе (условие проверяет RequestService.cancel)
        assertThat(READY_FOR_ISSUE.canTransitionTo(CANCELLED)).isTrue();
    }
}