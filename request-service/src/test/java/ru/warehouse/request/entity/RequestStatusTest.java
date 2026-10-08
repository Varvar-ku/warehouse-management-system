// src/test/java/ru/warehouse/request/entity/RequestStatusTest.java
package ru.warehouse.request.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static ru.warehouse.request.entity.RequestStatus.*;

class RequestStatusTest {

    @Test
    void happyPath_followsTheTzTable() {
        assertThat(DRAFT.canTransitionTo(SUBMITTED)).isTrue();
        assertThat(SUBMITTED.canTransitionTo(UNDER_REVIEW)).isTrue();
        assertThat(UNDER_REVIEW.canTransitionTo(APPROVED)).isTrue();
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
        assertThat(CLOSED.canTransitionTo(DRAFT)).isFalse();       // CLOSED конечный
        assertThat(REJECTED.canTransitionTo(RESERVED)).isFalse();
    }

    @Test
    void addedTransitions_areAllowed() {
        assertThat(APPROVED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(RESERVED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(APPROVED.canTransitionTo(UNDER_REVIEW)).isTrue();
        assertThat(PARTIALLY_AVAILABLE.canTransitionTo(UNDER_REVIEW)).isTrue();
    }
}