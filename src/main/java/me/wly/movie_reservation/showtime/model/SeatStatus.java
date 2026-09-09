package me.wly.movie_reservation.showtime.model;

public enum SeatStatus {
        AVAILABLE("AVAILABLE"),
        LOCKED("LOCKED"),
        SOLD("SOLD");
        private final String status;
        SeatStatus(String status){
            this.status = status;
        }
        public String getStatus(){
            return status;
        }
}
