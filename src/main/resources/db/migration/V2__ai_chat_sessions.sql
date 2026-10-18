CREATE TABLE `ai_chat_session` (
  `id` varchar(36) NOT NULL,
  `user_id` bigint NOT NULL,
  `title` varchar(80) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `business_state_json` longtext DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ai_chat_session_user_updated` (`user_id`, `updated_at`),
  CONSTRAINT `fk_ai_chat_session_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_chat_turn` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(36) NOT NULL,
  `request_id` varchar(128) NOT NULL,
  `user_message` text NOT NULL,
  `status` varchar(16) NOT NULL,
  `response_type` varchar(32) DEFAULT NULL,
  `response_schema_version` int DEFAULT NULL,
  `response_json` longtext DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `completed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_chat_turn_session_request` (`session_id`, `request_id`),
  KEY `idx_ai_chat_turn_session_id` (`session_id`, `id`),
  CONSTRAINT `fk_ai_chat_turn_session` FOREIGN KEY (`session_id`) REFERENCES `ai_chat_session` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
