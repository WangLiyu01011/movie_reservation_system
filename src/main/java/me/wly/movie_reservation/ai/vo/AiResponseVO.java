package me.wly.movie_reservation.ai.vo;

import me.wly.movie_reservation.ai.protocol.AiResponseType;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Completed response snapshot; future streaming events can address blocks by id. */
public record AiResponseVO(
        int schemaVersion,
        AiResponseType type,
        List<AiBlock> blocks,
        List<ShowtimeQueryPage> showtimeQueries
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;

    public AiResponseVO {
        if (schemaVersion < MIN_SUPPORTED_SCHEMA_VERSION || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported AI response schema version");
        }
        Objects.requireNonNull(type);
        blocks = List.copyOf(blocks);
        showtimeQueries = List.copyOf(showtimeQueries);
        Set<String> ids = new HashSet<>();
        for (AiBlock block : blocks) {
            if (block.id().isBlank() || !ids.add(block.id())) {
                throw new IllegalArgumentException("Block ids must be nonblank and unique");
            }
        }
    }

    /** Metadata for every actual query, including empty and unselected pages. */
    public record ShowtimeQueryPage(String toolCallId, int page, int size,
                                   boolean hasMore, List<Long> returnedShowtimeIds) {
        public ShowtimeQueryPage {
            returnedShowtimeIds = List.copyOf(returnedShowtimeIds);
        }
    }
}
