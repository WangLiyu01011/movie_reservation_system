package me.wly.movie_reservation.payment;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.exception.*;
import me.wly.movie_reservation.payment.model.*;
import me.wly.movie_reservation.payment.vo.PaymentTaskVO;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class PaymentTaskService {
    private final PaymentTaskRepository repository;
    private final PaymentCompensationProperties properties;
    private final Clock businessClock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(PaymentTransaction payment, PaymentTaskType type) {
        if (repository.findByPayment_IdAndType(payment.getId(), type).isPresent()) return;
        PaymentTask task = new PaymentTask();
        task.setPayment(payment);
        task.setType(type);
        task.setStatus(PaymentTaskStatus.PENDING);
        task.setNextAttemptAt(now());
        repository.save(task);
    }

    /** A late create response can arrive after another worker already finished a NOT_FOUND check. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void wakeSyncAfterLateCreate(PaymentTransaction payment) {
        PaymentTask task = repository.findByPayment_IdAndType(payment.getId(), PaymentTaskType.SYNC).orElseThrow();
        task.setStatus(PaymentTaskStatus.PENDING);
        task.setNextAttemptAt(now());
        task.setAttempts(0);
        clearLease(task); // Invalidates any older worker whose snapshot predates this provider creation.
    }

    @Transactional(readOnly = true)
    public List<Long> dueIds() {
        return repository.findDueIds(now(), PageRequest.of(0, properties.getBatchSize()));
    }

    @Transactional(readOnly = true)
    public Long taskId(String paymentNo, PaymentTaskType type) {
        return repository.findByPayment_PaymentNoAndType(paymentNo, type).map(PaymentTask::getId).orElse(null);
    }

    /** A short transaction claims a lease; no lock survives a provider call. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(Long id) {
        PaymentTask task = repository.findByIdForUpdate(id).orElse(null);
        LocalDateTime now = now();
        if (task == null || task.getStatus() == PaymentTaskStatus.DONE || task.getStatus() == PaymentTaskStatus.MANUAL) return null;
        if (task.getStatus() == PaymentTaskStatus.RUNNING && task.getLeaseUntil().isAfter(now)) return null;
        if (task.getStatus() == PaymentTaskStatus.PENDING && task.getNextAttemptAt().isAfter(now)) return null;
        if (task.getAttempts() >= properties.getMaxAttempts()) {
            task.setStatus(PaymentTaskStatus.MANUAL);
            task.setLastError("Retry limit reached; inspect provider state before retrying");
            clearLease(task);
            return null;
        }
        task.setStatus(PaymentTaskStatus.RUNNING);
        task.setAttempts(task.getAttempts() + 1);
        task.setLeaseToken(UUID.randomUUID().toString());
        task.setLeaseUntil(now.plus(properties.getLease()));
        return new Claim(task.getId(), task.getPayment().getPaymentNo(), task.getType(), task.getLeaseToken());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Claim claim, boolean terminal) {
        PaymentTask task = owned(claim);
        if (task == null) return;
        task.setStatus(terminal ? PaymentTaskStatus.DONE : PaymentTaskStatus.PENDING);
        task.setAttempts(0);
        task.setLastError(null);
        task.setNextAttemptAt(now().plus(properties.getPollInterval()));
        clearLease(task);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Claim claim, String message, boolean manual) {
        PaymentTask task = owned(claim);
        if (task == null) return;
        task.setStatus(manual || task.getAttempts() >= properties.getMaxAttempts()
                ? PaymentTaskStatus.MANUAL : PaymentTaskStatus.PENDING);
        task.setLastError(message.substring(0, Math.min(512, message.length())));
        task.setNextAttemptAt(now().plusSeconds(Math.min(300, 5L << Math.min(task.getAttempts() - 1, 6))));
        clearLease(task);
    }

    @Transactional(readOnly = true)
    public List<PaymentTaskVO> manualTasks() {
        return repository.findByStatusOrderById(PaymentTaskStatus.MANUAL, PageRequest.of(0, properties.getBatchSize()))
                .stream().map(task -> new PaymentTaskVO(task.getId(), task.getPayment().getPaymentNo(), task.getType(),
                        task.getStatus(), task.getAttempts(), task.getNextAttemptAt(), task.getLastError())).toList();
    }

    @Transactional
    public void retryManual(Long id) {
        PaymentTask task = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "Compensation task not found"));
        if (task.getStatus() != PaymentTaskStatus.MANUAL) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Only manual tasks can be retried");
        }
        task.setAttempts(0);
        task.setStatus(PaymentTaskStatus.PENDING);
        task.setNextAttemptAt(now());
        clearLease(task);
    }

    private PaymentTask owned(Claim claim) {
        return repository.findByIdForUpdate(claim.id()).filter(task -> task.getStatus() == PaymentTaskStatus.RUNNING
                && Objects.equals(task.getLeaseToken(), claim.token())).orElse(null);
    }
    private void clearLease(PaymentTask task) { task.setLeaseToken(null); task.setLeaseUntil(null); }
    private LocalDateTime now() { return LocalDateTime.now(businessClock); }
    public record Claim(Long id, String paymentNo, PaymentTaskType type, String token) { }
}
