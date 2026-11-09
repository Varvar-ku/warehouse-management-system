// ReceiptItemRepository.java
package ru.warehouse.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.warehouse.inventory.entity.Receipt;
import ru.warehouse.inventory.entity.ReceiptItem;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReceiptItemRepository extends JpaRepository<ReceiptItem, UUID> {

    /** Запланированная, но ещё не размещённая занятость секций (по другим поступлениям). */
    @Query("select i.targetSectionId as sectionId, i.unitCode as unitCode, sum(i.quantity) as qty "
            + "from ReceiptItem i join i.receipt r "
            + "where r.warehouseId = :warehouseId and r.status in :statuses and i.placementStatus = :pending "
            + "and i.targetSectionId is not null and r.id <> :excludeReceiptId "
            + "group by i.targetSectionId, i.unitCode")
    List<StockRepository.UsageRow> pendingUsage(@Param("warehouseId") UUID warehouseId,
                                                @Param("statuses") Collection<Receipt.Status> statuses,
                                                @Param("pending") ReceiptItem.PlacementStatus pending,
                                                @Param("excludeReceiptId") UUID excludeReceiptId);

    /** Запланировано, но не размещено в секцию другими позициями (кроме excludeItemId), в данной единице. */
    @Query("select coalesce(sum(i.quantity), 0) from ReceiptItem i join i.receipt r "
            + "where i.targetSectionId = :sectionId and i.unitCode = :unitCode and r.status in :statuses "
            + "and i.placementStatus = :pending and i.id <> :excludeItemId")
    BigDecimal pendingForSection(@Param("sectionId") UUID sectionId,
                                 @Param("unitCode") String unitCode,
                                 @Param("statuses") Collection<Receipt.Status> statuses,
                                 @Param("pending") ReceiptItem.PlacementStatus pending,
                                 @Param("excludeItemId") UUID excludeItemId);

    /** Незавершённые размещения в секцию: ожидают размещения или лежат в ней как расхождение. */
    @Query("select count(i) from ReceiptItem i join i.receipt r where r.status in :statuses and "
            + "((i.targetSectionId = :sectionId and i.placementStatus = :pending) "
            + "or (i.placedSectionId = :sectionId and i.placementStatus = :discrepancy))")
    long countOpenPlacements(@Param("sectionId") UUID sectionId,
                             @Param("statuses") Collection<Receipt.Status> statuses,
                             @Param("pending") ReceiptItem.PlacementStatus pending,
                             @Param("discrepancy") ReceiptItem.PlacementStatus discrepancy);

    @Query("select distinct i.targetSectionId as sectionId, i.productId as productId "
            + "from ReceiptItem i join i.receipt r "
            + "where r.warehouseId = :warehouseId and r.status in :statuses and i.placementStatus = :pending "
            + "and i.targetSectionId is not null and r.id <> :excludeReceiptId")
    List<StockRepository.SectionProductRow> pendingProducts(@Param("warehouseId") UUID warehouseId,
                                                            @Param("statuses") Collection<Receipt.Status> statuses,
                                                            @Param("pending") ReceiptItem.PlacementStatus pending,
                                                            @Param("excludeReceiptId") UUID excludeReceiptId);
}