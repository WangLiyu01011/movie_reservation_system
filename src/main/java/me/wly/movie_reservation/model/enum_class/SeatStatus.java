package me.wly.movie_reservation.model.enum_class;

public enum SeatStatus {
        RESERVED("RESERVED"),
        AVAILABLE("AVAILABLE");
        private String status;
        SeatStatus(String status){
            this.status = status;
        }
        public String getStatus(){
            return status;
        }
}

