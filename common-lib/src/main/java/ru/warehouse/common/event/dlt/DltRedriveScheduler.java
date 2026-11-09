package ru.warehouse.common.event.dlt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Периодически возвращает сообщения из DLT в исходные топики (см. {@link DltRedriver}). */
public class DltRedriveScheduler {
    private static final Logger log = LoggerFactory.getLogger(DltRedriveScheduler.class);

    private final DltRedriver redriver;

    public DltRedriveScheduler(DltRedriver redriver) {
        this.redriver = redriver;
    }

    @Scheduled(fixedDelayString = "${wms.dlt.redrive.interval-ms:300000}",
            initialDelayString = "${wms.dlt.redrive.initial-delay-ms:60000}")
    public void run() {
        try {
            DltRedriver.Result result = redriver.redrive();
            if (result.redriven() > 0 || result.parked() > 0) {
                log.warn("DLT: возвращено сообщений {}, запарковано {}", result.redriven(), result.parked());
            }
        } catch (RuntimeException ex) {
            log.warn("DLT: прогон возврата не удался: {}", ex.toString());
        }
    }
}
