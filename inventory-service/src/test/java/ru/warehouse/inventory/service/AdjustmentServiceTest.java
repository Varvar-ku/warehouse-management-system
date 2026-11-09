// AdjustmentServiceTest.java
package ru.warehouse.inventory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.warehouse.common.event.OutboxPublisher;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.inventory.entity.*;
import ru.warehouse.inventory.repository.StockAdjustmentRepository;
import ru.warehouse.inventory.repository.StockMovementRepository;
import ru.warehouse.inventory.repository.StockRepository;
import ru.warehouse.inventory.repository.StockShortageRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** B1: правила документа корректировки, не требующие базы данных. */
@ExtendWith(MockitoExtension.class)
class AdjustmentServiceTest {

    @Mock StockAdjustmentRepository adjustmentRepository;
    @Mock StockShortageRepository shortageRepository;
    @Mock StockRepository stockRepository;
    @Mock StockMovementRepository movementRepository;
    @Mock ShortageService shortageService;
    @Mock MovementEvents movementEvents;
    @Mock OutboxPublisher outbox;
    @InjectMocks AdjustmentService service;

    final UUID author = UUID.randomUUID();
    final UUID admin = UUID.randomUUID();
    StockAdjustment adjustment;
    StockShortage shortage;
    UUID id;

    @BeforeEach
    void setUp() {
        Batch batch = new Batch();
        batch.setProductSku("SKU");
        batch.setProductName("Test");
        batch.setBatchNumber("L1");
        Stock stock = new Stock();
        stock.setSectionCode("A-01");
        stock.setUnitCode("PCS");
        stock.setBatch(batch);

        shortage = new StockShortage();
        shortage.setNumber("SH-2026-000001");
        shortage.setStock(stock);
        shortage.setQuantity(new BigDecimal("3"));
        shortage.setAdjustmentId(UUID.randomUUID());

        StockAdjustmentItem item = new StockAdjustmentItem();
        item.setStock(stock);
        item.setShortage(shortage);
        item.setQuantity(new BigDecimal("3"));

        adjustment = new StockAdjustment();
        adjustment.setNumber("AD-2026-000001");
        adjustment.setCreatedBy(author);
        adjustment.setComment("недостача");
        adjustment.getItems().add(item);
        adjustment.setCreatedAt(OffsetDateTime.now());
        UUID id = UUID.randomUUID();
        when(adjustmentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(adjustment));
        this.id = id;
    }

    @Test
    void authorCannotApproveOwnAdjustment() {
        assertThatThrownBy(() -> service.approve(id, author)).isInstanceOf(ApiException.class);
        assertThat(adjustment.getApprovedAt()).isNull();
    }

    @Test
    void anotherUserCanApprove() {
        service.approve(id, admin);
        assertThat(adjustment.getApprovedBy()).isEqualTo(admin);
        assertThat(adjustment.getApprovedAt()).isNotNull();
    }

    @Test
    void cannotConfirmWithoutApproval() {
        assertThatThrownBy(() -> service.confirm(id, author)).isInstanceOf(ApiException.class);
        verifyNoInteractions(movementRepository, movementEvents);
        assertThat(adjustment.getStatus()).isEqualTo(StockAdjustment.Status.DRAFT);
    }

    @Test
    void cancelReleasesShortagesForAnotherDecision() {
        service.cancel(id, "передумали", author);
        assertThat(adjustment.getStatus()).isEqualTo(StockAdjustment.Status.CANCELLED);
        assertThat(shortage.getAdjustmentId()).isNull();
        assertThat(shortage.getStatus()).isEqualTo(StockShortage.Status.OPEN);
    }
}
