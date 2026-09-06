package me.wly.movie_reservation.model.enum_class;

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

