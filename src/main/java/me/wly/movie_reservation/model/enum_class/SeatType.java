package me.wly.movie_reservation.model.enum_class;

public enum SeatType {
    EMPTY("EMPTY"), // for setting aisles or other non-seat unit
    NORMAL("NORMAL"),
    VIP("VIP");

    private String type;

    SeatType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
