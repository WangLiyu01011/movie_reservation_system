package me.wly.movie_reservation.movie.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** TMDB genres, with an explicit whitelist rather than arbitrary natural-language SQL filters. */
public enum MovieGenre {
    ACTION("动作", "Action"), ADVENTURE("冒险", "Adventure"), ANIMATION("动画", "Animation"),
    COMEDY("喜剧", "Comedy", "搞笑"), CRIME("犯罪", "Crime"), DOCUMENTARY("纪录", "Documentary"),
    DRAMA("剧情", "Drama"), FAMILY("家庭", "Family"), FANTASY("奇幻", "Fantasy"),
    HISTORY("历史", "History"), HORROR("恐怖", "Horror"), MUSIC("音乐", "Music"),
    MYSTERY("悬疑", "Mystery"), ROMANCE("爱情", "Romance", "浪漫"),
    SCIENCE_FICTION("科幻", "Science Fiction", "sci-fi", "science-fiction"),
    TV_MOVIE("电视电影", "TV Movie"), THRILLER("惊悚", "Thriller"), WAR("战争", "War"),
    WESTERN("西部", "Western");

    private final String label;
    private final String englishLabel;
    private final List<String> aliases;

    MovieGenre(String label, String englishLabel, String... aliases) {
        this.label = label;
        this.englishLabel = englishLabel;
        this.aliases = List.of(aliases);
    }

    public String label() { return label; }
    public String englishLabel() { return englishLabel; }

    public static List<String> supportedLabels() {
        return Arrays.stream(values()).map(MovieGenre::label).toList();
    }

    public static Optional<MovieGenre> fromKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return Optional.empty();
        String normalized = keyword.strip().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("片")) normalized = normalized.substring(0, normalized.length() - 1);
        final String candidate = normalized;
        return Arrays.stream(values()).filter(genre -> genre.label.equals(candidate)
                || genre.englishLabel.toLowerCase(Locale.ROOT).equals(candidate)
                || genre.aliases.contains(candidate)).findFirst();
    }
}
