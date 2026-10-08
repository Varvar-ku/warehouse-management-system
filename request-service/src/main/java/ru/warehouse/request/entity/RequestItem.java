// RequestItem.java
package ru.warehouse.request.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "request_items")
@Getter @Setter
public class RequestItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @Column(name = "line_no", nullable = false) private int lineNo;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "product_sku", nullable = false, length = 100) private String productSku;
    @Column(name = "product_name", nullable = false) private String productName;
    @Column(name = "unit_code", nullable = false, length = 20) private String unitCode;
    @Column(name = "unit_allow_fractions", nullable = false) private boolean unitAllowFractions = true;

    @Column(name = "requested_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal requestedQuantity;

    /** Решение кладовщика по результатам проверки наличия; null до проверки. */
    @Column(name = "approved_quantity", precision = 18, scale = 4)
    private BigDecimal approvedQuantity;

    @Column(name = "reserved_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Column(name = "issued_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal issuedQuantity = BigDecimal.ZERO;

    private String comment;
}