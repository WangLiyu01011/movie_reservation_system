package me.wly.movie_reservation.ai.protocol;

/** The primary intent of one completed assistant turn. */
public enum AiResponseType {
    MOVIE_RECOMMENDATION,
    SHOWTIME_OPTIONS,
    CLARIFICATION,
    NO_RESULTS,
    GENERAL
}
