package me.wly.movie_reservation.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j @Component @RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.compensation.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentCompensationScheduler {
    private final PaymentTaskService tasks;
    private final PaymentCompensationWorker worker;

    @Scheduled(fixedDelayString = "${payment.compensation.scan-delay:5s}",
            initialDelayString = "${payment.compensation.initial-delay:10s}")
    public void compensate() {
        for (Long id : tasks.dueIds()) {
            try { worker.runOne(id); }
            catch (RuntimeException exception) {
                log.warn("Cannot claim or finish payment task {}: {}", id, exception.getClass().getSimpleName());
            }
        }
    }
}
