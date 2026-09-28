-- Baseline for fresh databases; existing V1 installations remain valid.
-- This affects this migration connection only. Runtime connections also set time_zone via Hikari.
SET SESSION time_zone = '+08:00';

CREATE TABLE `area` (
  `id` int NOT NULL,
  `name` char(32) NOT NULL,
  `parent_id` int NOT NULL COMMENT 'id of parent area, top-level parent_id is 0',
  `level` smallint NOT NULL COMMENT '0 province, 1 city, 2 county or district',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(80) NOT NULL,
  `username` char(32) NOT NULL,
  `nickname` varchar(48) NOT NULL,
  `email_address` varchar(60) DEFAULT NULL,
  `phone_number` varchar(20) DEFAULT NULL,
  `password` varchar(80) NOT NULL,
  `user_role` varchar(32) NOT NULL DEFAULT 'CUSTOMER',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_code` (`code`),
  UNIQUE KEY `uk_user_email_address` (`email_address`),
  UNIQUE KEY `uk_user_phone_number` (`phone_number`),
  CONSTRAINT `email_address_or_phone_number_is_not_null`
    CHECK (`email_address` IS NOT NULL OR `phone_number` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `movie` (
  `id` int NOT NULL AUTO_INCREMENT,
  `imdb_id` varchar(12) DEFAULT NULL,
  `release_date` datetime DEFAULT NULL,
  `off_date` datetime DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `description` text,
  `genres` json DEFAULT NULL,
  `language` varchar(45) DEFAULT NULL,
  `poster_imageurl` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `imdbId_UNIQUE` (`imdb_id`),
  KEY `idx_movie_release_date` (`release_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `theater` (
  `id` int NOT NULL AUTO_INCREMENT,
  `theater_name` varchar(100) NOT NULL,
  `location` varchar(255) NOT NULL,
  `city_id` int NOT NULL,
  `district_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_theater_district_name` (`district_id`, `theater_name`),
  KEY `idx_city_district` (`city_id`, `district_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `hall` (
  `id` int NOT NULL AUTO_INCREMENT,
  `theater_id` int NOT NULL,
  `type` char(20) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `name` varchar(40) NOT NULL,
  `row_count` int NOT NULL,
  `column_count` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hall_theater_name` (`theater_id`, `name`),
  CONSTRAINT `hall_theater_id_fk`
    FOREIGN KEY (`theater_id`) REFERENCES `theater` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `seat` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `x` int NOT NULL,
  `y` int NOT NULL,
  `hall_id` int NOT NULL,
  `seat_type` varchar(20) NOT NULL,
  `seat_label` varchar(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seat_hall_position` (`hall_id`, `x`, `y`),
  UNIQUE KEY `uk_seat_hall_label` (`hall_id`, `seat_label`),
  CONSTRAINT `fk_seat_hall`
    FOREIGN KEY (`hall_id`) REFERENCES `hall` (`id`),
  CONSTRAINT `chk_seat_positive_position`
    CHECK (`x` > 0 AND `y` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `showtime` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `price` decimal(5,2) NOT NULL,
  `theater_id` int NOT NULL,
  `theater_name` varchar(100) NOT NULL,
  `hall_id` int NOT NULL,
  `hall_name` varchar(40) NOT NULL,
  `movie_id` int NOT NULL,
  `movie_title` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `Showtime_movie_id_fk` (`movie_id`),
  KEY `idx_showtime_theater_movie_start` (`theater_id`, `movie_id`, `start_time`),
  KEY `idx_showtime_hall_interval` (`hall_id`, `start_time`, `end_time`),
  CONSTRAINT `showtime_hall_id_fk`
    FOREIGN KEY (`hall_id`) REFERENCES `hall` (`id`),
  CONSTRAINT `showtime_movie_id_fk`
    FOREIGN KEY (`movie_id`) REFERENCES `movie` (`id`),
  CONSTRAINT `showtime_theater_id_fk`
    FOREIGN KEY (`theater_id`) REFERENCES `theater` (`id`),
  CONSTRAINT `chk_showtime_positive_price`
    CHECK (`price` > 0),
  CONSTRAINT `chk_showtime_time_range`
    CHECK (`end_time` > `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(80) NOT NULL,
  `user_id` bigint NOT NULL,
  `request_id` varchar(128) NOT NULL,
  `movie_title` varchar(80) DEFAULT NULL,
  `theater_name` varchar(80) DEFAULT NULL,
  `hall_name` varchar(40) DEFAULT NULL,
  `showtime_id` bigint NOT NULL,
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `total_price` decimal(10,2) NOT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `paid_at` datetime(3) DEFAULT NULL,
  `cancelled_at` datetime(3) DEFAULT NULL,
  `expires_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `orders_pk_2` (`code`),
  UNIQUE KEY `uk_orders_user_request` (`user_id`, `request_id`),
  KEY `idx_orders_status_expires_at` (`status`, `expires_at`),
  KEY `orders_showtime_id_fk` (`showtime_id`),
  CONSTRAINT `fk_orders_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `orders_showtime_id_fk`
    FOREIGN KEY (`showtime_id`) REFERENCES `showtime` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `showtime_seat` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `showtime_id` bigint NOT NULL,
  `seat_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'AVAILABLE',
  `order_id` bigint DEFAULT NULL,
  `lock_token` varchar(80) DEFAULT NULL,
  `lock_until` datetime(3) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  `seat_type_snapshot` varchar(20) NOT NULL,
  `seat_label_snapshot` varchar(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_showtime_seat` (`showtime_id`, `seat_id`),
  KEY `idx_showtime_seat_status` (`showtime_id`, `status`),
  KEY `fk_showtime_seat_seat` (`seat_id`),
  KEY `idx_showtime_seat_order_status` (`order_id`, `status`),
  CONSTRAINT `fk_showtime_seat_order`
    FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `fk_showtime_seat_seat`
    FOREIGN KEY (`seat_id`) REFERENCES `seat` (`id`),
  CONSTRAINT `fk_showtime_seat_showtime`
    FOREIGN KEY (`showtime_id`) REFERENCES `showtime` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_seat` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `showtime_seat_id` bigint NOT NULL,
  `ticket_price` decimal(10,2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_seat_order` (`order_id`),
  KEY `idx_order_seat_showtime_seat` (`showtime_seat_id`),
  CONSTRAINT `fk_order_seat_order`
    FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `fk_order_seat_showtime_seat`
    FOREIGN KEY (`showtime_seat_id`) REFERENCES `showtime_seat` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `payment_no` varchar(64) NOT NULL,
  `order_id` bigint NOT NULL,
  `request_id` varchar(64) NOT NULL,
  `channel` varchar(32) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `status` varchar(16) NOT NULL,
  `provider_trade_no` varchar(128) DEFAULT NULL,
  `failure_code` varchar(64) DEFAULT NULL,
  `failure_message` varchar(512) DEFAULT NULL,
  `pay_url` varchar(2048) DEFAULT NULL,
  `callback_payload` longtext,
  `paid_at` datetime DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `expires_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_transaction_no` (`payment_no`),
  UNIQUE KEY `uk_payment_transaction_order_request` (`order_id`, `request_id`),
  UNIQUE KEY `uk_payment_transaction_provider_trade` (`provider_trade_no`),
  KEY `idx_payment_transaction_order_status_id` (`order_id`, `status`, `id`),
  CONSTRAINT `fk_payment_transaction_order`
    FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_callback_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `channel` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payment_no` varchar(64) COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `provider_trade_no` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `callback_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `process_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `failure_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_message` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `raw_payload` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `processing_message` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duplicate_count` int NOT NULL DEFAULT 0,
  `received_at` datetime(3) NOT NULL,
  `last_received_at` datetime(3) NOT NULL,
  `processed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_callback_event_channel_event` (`channel`, `event_id`),
  KEY `idx_payment_callback_event_payment_no` (`payment_no`),
  CONSTRAINT `fk_payment_callback_event_payment_no`
    FOREIGN KEY (`payment_no`) REFERENCES `payment_transaction` (`payment_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `theater_admin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `theater_id` int NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_theater_admin_theater_user` (`theater_id`, `user_id`),
  KEY `theater_admin_users_id_fk` (`user_id`),
  CONSTRAINT `theater_admin_theater_id_fk`
    FOREIGN KEY (`theater_id`) REFERENCES `theater` (`id`),
  CONSTRAINT `theater_admin_users_id_fk`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TRIGGER `before_orders_insert`
BEFORE INSERT ON `orders`
FOR EACH ROW
SET NEW.`expires_at` = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 5 MINUTE);

CREATE TRIGGER `before_payment_transaction_insert`
BEFORE INSERT ON `payment_transaction`
FOR EACH ROW
SET NEW.`expires_at` = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 5 MINUTE);
