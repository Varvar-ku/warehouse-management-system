package ru.warehouse.inventory.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.inventory.dto.OperationDtos.*;
import ru.warehouse.inventory.entity.WriteOff;
import ru.warehouse.inventory.service.WriteOffService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** S6: списание причины LOST одобряет другой человек; до одобрения его нельзя провести; автор не одобряет сам. */
class WriteOffApprovalIntegrationTest extends IntegrationTestBase {
    @Autowired WriteOffService writeOffs;

    @Test
    void lostWriteOff_needsApproval_byAnotherPerson() {
        UUID warehouse = UUID.randomUUID(), product = UUID.randomUUID();
        UUID stock = StockFixtures.stock(jdbc, warehouse, product, StockFixtures.Spec.plain("A-1", "W", "10"));
        UUID author = UUID.randomUUID(), admin = UUID.randomUUID();

        WriteOffResponse draft = writeOffs.create(new CreateWriteOffRequest(warehouse, WriteOff.Reason.LOST,
                "потеряно при перевозке", List.of(new WriteOffItemRequest(stock, new BigDecimal("3")))), author);
        assertThat(draft.approvalRequired()).isTrue();

        assertThatThrownBy(() -> writeOffs.confirm(draft.id(), author))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("APPROVAL_REQUIRED");
        assertThatThrownBy(() -> writeOffs.approve(draft.id(), author))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("SELF_APPROVAL");

        writeOffs.approve(draft.id(), admin);
        writeOffs.confirm(draft.id(), author);

        assertThat(jdbc.queryForObject("SELECT quantity FROM stock WHERE id = ?", BigDecimal.class, stock))
                .isEqualByComparingTo("7");
    }

    @Test
    void writeOff_cannotExceedAvailable() {
        UUID warehouse = UUID.randomUUID(), product = UUID.randomUUID();
        UUID stock = StockFixtures.stock(jdbc, warehouse, product, StockFixtures.Spec.plain("A-1", "W2", "2"));

        assertThatThrownBy(() -> writeOffs.create(new CreateWriteOffRequest(warehouse, WriteOff.Reason.DAMAGED, null,
                List.of(new WriteOffItemRequest(stock, new BigDecimal("3")))), UUID.randomUUID()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INSUFFICIENT_AVAILABLE_STOCK");
    }
}
