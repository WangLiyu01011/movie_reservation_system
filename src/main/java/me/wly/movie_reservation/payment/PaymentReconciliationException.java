package me.wly.movie_reservation.payment;

/** Provider data mismatches need inspection; blind retries cannot repair them. */
public class PaymentReconciliationException extends RuntimeException {
    public PaymentReconciliationException(String message) { super(message); }
}
