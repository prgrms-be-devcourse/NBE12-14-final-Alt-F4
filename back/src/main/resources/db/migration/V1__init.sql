-- develop 엔티티 기준 첫 스키마
-- Hibernate(ddl-auto: create)로 빈 MySQL 8.0에 만든 뒤 mysqldump --no-data로 뽑았다
-- 테이블을 이름 순서로 만들기 때문에 FK가 아직 없는 테이블을 가리킬 수 있어 잠시 FK 검사를 끈다

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `calendar_groups` (
  `display_order` int DEFAULT NULL,
  `is_visible` bit(1) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKsajapwp60qcve29ql1r3xc24i` (`user_id`),
  CONSTRAINT `FKsajapwp60qcve29ql1r3xc24i` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `calendar_invitations` (
  `calendar_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL,
  `token_hash` char(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmri47hni0m1ywks76ywgdvfsr` (`calendar_id`),
  KEY `FK61rvjr4ybqx70ujdf8vnug6r6` (`user_id`),
  CONSTRAINT `FK61rvjr4ybqx70ujdf8vnug6r6` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKmri47hni0m1ywks76ywgdvfsr` FOREIGN KEY (`calendar_id`) REFERENCES `calendars` (`id`),
  CONSTRAINT `calendar_invitations_chk_1` CHECK ((`role` in (_utf8mb4'OWNER',_utf8mb4'EDITOR',_utf8mb4'VIEWER',_utf8mb4'SUBSCRIBER'))),
  CONSTRAINT `calendar_invitations_chk_2` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'REVOKED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `calendars` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `color` varchar(30) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `timezone` varchar(100) NOT NULL,
  `description` text,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `category_options` (
  `is_visible` bit(1) NOT NULL,
  `calendar_id` bigint NOT NULL,
  `category_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `subscribe_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `color` varchar(30) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKtkefc4kl2rw4ro9ifl2v6n9bh` (`calendar_id`),
  KEY `FKm3i9r7h3qm6vb117186h8tmpj` (`category_id`),
  KEY `FK7fa6nnikluc74he01a2kug90q` (`subscribe_id`),
  CONSTRAINT `FK7fa6nnikluc74he01a2kug90q` FOREIGN KEY (`subscribe_id`) REFERENCES `subscribes` (`id`),
  CONSTRAINT `FKm3i9r7h3qm6vb117186h8tmpj` FOREIGN KEY (`category_id`) REFERENCES `event_categories` (`id`),
  CONSTRAINT `FKtkefc4kl2rw4ro9ifl2v6n9bh` FOREIGN KEY (`calendar_id`) REFERENCES `calendars` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `event_categories` (
  `calendar_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `color` varchar(30) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdvd3d1qv1wyh5pxka1gptmv0n` (`calendar_id`),
  CONSTRAINT `FKdvd3d1qv1wyh5pxka1gptmv0n` FOREIGN KEY (`calendar_id`) REFERENCES `calendars` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `event_exceptions` (
  `created_at` datetime(6) NOT NULL,
  `end_at` datetime(6) DEFAULT NULL,
  `event_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recurrence_id` datetime(6) NOT NULL,
  `start_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `type` varchar(20) NOT NULL,
  `title` varchar(200) DEFAULT NULL,
  `description` text,
  `location` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKrt0c3ibboy2m0ixe7gv988mnd` (`event_id`),
  CONSTRAINT `FKrt0c3ibboy2m0ixe7gv988mnd` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `event_exceptions_chk_1` CHECK ((`type` in (_utf8mb4'DELETED',_utf8mb4'MODIFIED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `event_options` (
  `is_visible` bit(1) NOT NULL,
  `calendar_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `event_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `subscribe_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqi6qdd9cmi7uxhwo3vcq8ok8n` (`calendar_id`),
  KEY `FK8yxytvpin6xt6jl7sgcjdxlr` (`event_id`),
  KEY `FK64h2eo12bje981k6ca79ctpgp` (`subscribe_id`),
  CONSTRAINT `FK64h2eo12bje981k6ca79ctpgp` FOREIGN KEY (`subscribe_id`) REFERENCES `subscribes` (`id`),
  CONSTRAINT `FK8yxytvpin6xt6jl7sgcjdxlr` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `FKqi6qdd9cmi7uxhwo3vcq8ok8n` FOREIGN KEY (`calendar_id`) REFERENCES `calendars` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `events` (
  `is_all_day` bit(1) NOT NULL,
  `calendar_id` bigint NOT NULL,
  `category_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `end_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recur_until` datetime(6) DEFAULT NULL,
  `start_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `timezone` varchar(100) NOT NULL,
  `title` varchar(200) NOT NULL,
  `description` text,
  `location` varchar(255) DEFAULT NULL,
  `rrule` varchar(255) DEFAULT NULL,
  `uid` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKsr217wml5c5jdpgd2ro6k8omx` (`calendar_id`),
  KEY `FKjgwxobdeneisw8h3nlaa2fwax` (`category_id`),
  KEY `FKat8p3s7yjcp57lny4udqvqncq` (`user_id`),
  CONSTRAINT `FKat8p3s7yjcp57lny4udqvqncq` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKjgwxobdeneisw8h3nlaa2fwax` FOREIGN KEY (`category_id`) REFERENCES `event_categories` (`id`),
  CONSTRAINT `FKsr217wml5c5jdpgd2ro6k8omx` FOREIGN KEY (`calendar_id`) REFERENCES `calendars` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notifications` (
  `is_read` bit(1) NOT NULL,
  `calendar_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` varchar(50) NOT NULL,
  `params` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK2nfaiiyxfd8t1jvnu3gc7rs6y` (`calendar_id`),
  KEY `FK9y21adhxn0ayjhfocscqox7bh` (`user_id`),
  CONSTRAINT `FK2nfaiiyxfd8t1jvnu3gc7rs6y` FOREIGN KEY (`calendar_id`) REFERENCES `calendars` (`id`),
  CONSTRAINT `FK9y21adhxn0ayjhfocscqox7bh` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `notifications_chk_1` CHECK ((`type` in (_utf8mb4'EVENT_CREATED',_utf8mb4'EVENT_UPDATED',_utf8mb4'EVENT_CANCELLED',_utf8mb4'EVENT_DELETED',_utf8mb4'MEMBER_JOINED',_utf8mb4'ROOM_CREATED',_utf8mb4'ROOM_CONFIRMED',_utf8mb4'CALENDAR_DELETED',_utf8mb4'GROUP_REQUEST_RECEIVED',_utf8mb4'GROUP_REQUEST_APPROVED',_utf8mb4'GROUP_REQUEST_REJECTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `refresh_tokens` (
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `token_hash` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_tokens_token_hash` (`token_hash`),
  KEY `idx_refresh_tokens_user_id` (`user_id`),
  CONSTRAINT `FK1lih5y2npsf8u5o3vhdb9y0os` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `room_invitations` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invitee_id` bigint NOT NULL,
  `inviter_id` bigint NOT NULL,
  `responded_at` datetime(6) DEFAULT NULL,
  `room_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `status` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6eo1fn2bhwpyrsup7f12fn0a1` (`invitee_id`),
  KEY `FKd9v2tk7xfwfy166d71o1sh43o` (`inviter_id`),
  KEY `FKmx8ttt2afcguihccyydne99vr` (`room_id`),
  CONSTRAINT `FK6eo1fn2bhwpyrsup7f12fn0a1` FOREIGN KEY (`invitee_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKd9v2tk7xfwfy166d71o1sh43o` FOREIGN KEY (`inviter_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKmx8ttt2afcguihccyydne99vr` FOREIGN KEY (`room_id`) REFERENCES `scheduling_rooms` (`id`),
  CONSTRAINT `room_invitations_chk_1` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'ACCEPTED',_utf8mb4'DECLINED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `room_invite_links` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `revoked_at` datetime(6) DEFAULT NULL,
  `room_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `token_hash` char(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKto8io9nvnj8hmafcsmin3mrn0` (`room_id`),
  KEY `FKn49t5kx9mpida3ojwcw7nxah1` (`user_id`),
  CONSTRAINT `FKn49t5kx9mpida3ojwcw7nxah1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKto8io9nvnj8hmafcsmin3mrn0` FOREIGN KEY (`room_id`) REFERENCES `scheduling_rooms` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `room_members` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `joined_at` datetime(6) NOT NULL,
  `room_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKtb57dxxy1rcd4om4cwfxjwntj` (`room_id`),
  KEY `FKmcymqhedxe30d98p07eeqo3my` (`user_id`),
  CONSTRAINT `FKmcymqhedxe30d98p07eeqo3my` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKtb57dxxy1rcd4om4cwfxjwntj` FOREIGN KEY (`room_id`) REFERENCES `scheduling_rooms` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `room_messages` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `content` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK3x9iqtvdg7n37attdjkmcl3mp` (`member_id`),
  CONSTRAINT `FK3x9iqtvdg7n37attdjkmcl3mp` FOREIGN KEY (`member_id`) REFERENCES `room_members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `room_shared_events` (
  `created_at` datetime(6) NOT NULL,
  `event_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `display_mode` varchar(30) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgsgmvxwu4mv8yfs7rsqyynl85` (`event_id`),
  KEY `FK29wernq6tob1c286dl2m7x1ho` (`member_id`),
  CONSTRAINT `FK29wernq6tob1c286dl2m7x1ho` FOREIGN KEY (`member_id`) REFERENCES `room_members` (`id`),
  CONSTRAINT `FKgsgmvxwu4mv8yfs7rsqyynl85` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `room_shared_events_chk_1` CHECK ((`display_mode` in (_utf8mb4'TIME_ONLY',_utf8mb4'CALENDAR_NAME_AND_TIME')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sample_entity` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `string` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `scheduling_rooms` (
  `daily_end_time` time NOT NULL,
  `daily_start_time` time NOT NULL,
  `end_date` date NOT NULL,
  `start_date` date NOT NULL,
  `calendar_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(100) NOT NULL,
  `timezone` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKd027akddv3rykgm06c0ytrhay` (`calendar_id`),
  KEY `FKkpwyetav6s0ir2ot70r4226mn` (`owner_id`),
  CONSTRAINT `FKd027akddv3rykgm06c0ytrhay` FOREIGN KEY (`calendar_id`) REFERENCES `calendars` (`id`),
  CONSTRAINT `FKkpwyetav6s0ir2ot70r4226mn` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `share_calendar_connection` (
  `display_order` int DEFAULT NULL,
  `calendar_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `share_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK84hhkow1bw1qolpaoyvw64pe9` (`calendar_id`),
  KEY `FK1uhbm7bka2tv4i73xqa926nr9` (`share_id`),
  CONSTRAINT `FK1uhbm7bka2tv4i73xqa926nr9` FOREIGN KEY (`share_id`) REFERENCES `share_info` (`id`),
  CONSTRAINT `FK84hhkow1bw1qolpaoyvw64pe9` FOREIGN KEY (`calendar_id`) REFERENCES `calendars` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `share_info` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `group_name` varchar(100) DEFAULT NULL,
  `content` text,
  `title` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK92uo8c185q6nlh60ro9s3yssc` (`user_id`),
  CONSTRAINT `FK92uo8c185q6nlh60ro9s3yssc` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `share_tag_connection` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `share_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKb8wcx56k2xufb6s95bkw2jf6t` (`share_id`),
  KEY `FK7n2v3moclx074sxq22p81vuqk` (`tag_id`),
  CONSTRAINT `FK7n2v3moclx074sxq22p81vuqk` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`),
  CONSTRAINT `FKb8wcx56k2xufb6s95bkw2jf6t` FOREIGN KEY (`share_id`) REFERENCES `share_info` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `subscribes` (
  `is_visible` bit(1) NOT NULL,
  `calendar_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `group_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `role` varchar(20) NOT NULL,
  `color` varchar(30) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subscribes_user_calendar` (`user_id`,`calendar_id`),
  KEY `FK8xsw3oax3a34evyqxtd1s9pbv` (`calendar_id`),
  KEY `FKocd0jil2ebixrdh3qvh0b2kgn` (`group_id`),
  CONSTRAINT `FK6hen8xwilkn7nbablqbqs6nkg` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK8xsw3oax3a34evyqxtd1s9pbv` FOREIGN KEY (`calendar_id`) REFERENCES `calendars` (`id`),
  CONSTRAINT `FKocd0jil2ebixrdh3qvh0b2kgn` FOREIGN KEY (`group_id`) REFERENCES `calendar_groups` (`id`),
  CONSTRAINT `subscribes_chk_1` CHECK ((`role` in (_utf8mb4'OWNER',_utf8mb4'EDITOR',_utf8mb4'VIEWER',_utf8mb4'SUBSCRIBER')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `tag` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `nickname` varchar(50) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
