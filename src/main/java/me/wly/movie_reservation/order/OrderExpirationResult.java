package me.wly.movie_reservation.order;

public enum OrderExpirationResult {
    EXPIRED(true),
    ALREADY_TERMINAL(true),
    NOT_FOUND(true),
    NOT_DUE(false);

    private final boolean removeFromZSet;

    OrderExpirationResult(boolean removeFromZSet) {
        this.removeFromZSet = removeFromZSet;
    }

    public boolean shouldRemoveFromZSet() {
        return removeFromZSet;
    }
}
