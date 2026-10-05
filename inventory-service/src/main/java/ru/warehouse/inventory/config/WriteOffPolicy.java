// config/WriteOffPolicy.java
package ru.warehouse.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.warehouse.inventory.entity.WriteOff;
import java.util.EnumSet;
import java.util.Set;

/** Политика списания (разд. 14 ТЗ: «при необходимости комментарий или подтверждение администратора»). */
@ConfigurationProperties(prefix = "app.write-off")
public record WriteOffPolicy(Set<WriteOff.Reason> commentRequiredReasons, Set<WriteOff.Reason> approvalReasons) {
    public WriteOffPolicy {
        if (commentRequiredReasons == null) {
            commentRequiredReasons = EnumSet.of(WriteOff.Reason.LOST, WriteOff.Reason.OTHER);
        }
        if (approvalReasons == null) {
            approvalReasons = EnumSet.of(WriteOff.Reason.LOST);
        }
    }
}
