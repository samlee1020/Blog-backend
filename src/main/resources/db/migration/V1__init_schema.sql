CREATE TABLE IF NOT EXISTS `user` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted_at DATETIME NULL,
  UNIQUE KEY uk_user_username (username),
  KEY idx_user_role (role),
  KEY idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  slug VARCHAR(100) NOT NULL,
  description VARCHAR(255) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted_at DATETIME NULL,
  UNIQUE KEY uk_category_slug (slug),
  KEY idx_category_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  slug VARCHAR(100) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted_at DATETIME NULL,
  UNIQUE KEY uk_tag_name (name),
  UNIQUE KEY uk_tag_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS article (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  slug VARCHAR(200) NOT NULL,
  summary VARCHAR(500) NULL,
  cover_image_url VARCHAR(500) NULL,
  content_markdown LONGTEXT NOT NULL,
  content_html LONGTEXT NULL,
  category_id BIGINT NULL,
  status VARCHAR(16) NOT NULL,
  view_count BIGINT NOT NULL DEFAULT 0,
  published_at DATETIME NULL,
  created_by BIGINT NOT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted_at DATETIME NULL,
  UNIQUE KEY uk_article_slug (slug),
  KEY idx_article_status_published_at (status, published_at),
  KEY idx_article_category_id (category_id),
  KEY idx_article_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS article_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  article_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_article_tag (article_id, tag_id),
  KEY idx_article_tag_tag_id (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS comment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  article_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content VARCHAR(2000) NOT NULL,
  status VARCHAR(16) NOT NULL,
  ip_address VARCHAR(64) NULL,
  user_agent VARCHAR(500) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted_at DATETIME NULL,
  KEY idx_comment_article_status_created (article_id, status, created_at),
  KEY idx_comment_user_id (user_id),
  KEY idx_comment_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cover_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  subtitle VARCHAR(500) NULL,
  background_image_url VARCHAR(500) NULL,
  avatar_image_url VARCHAR(500) NULL,
  links_json JSON NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  KEY idx_cover_config_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS profile_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  display_name VARCHAR(100) NOT NULL,
  bio TEXT NULL,
  avatar_image_url VARCHAR(500) NULL,
  email VARCHAR(100) NULL,
  location VARCHAR(100) NULL,
  social_links_json JSON NULL,
  content_markdown LONGTEXT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  KEY idx_profile_config_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS media_asset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  original_filename VARCHAR(255) NOT NULL,
  stored_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  file_size BIGINT NOT NULL,
  storage_type VARCHAR(16) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  url VARCHAR(500) NOT NULL,
  usage_type VARCHAR(32) NOT NULL,
  uploaded_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  deleted_at DATETIME NULL,
  KEY idx_media_asset_usage_type (usage_type),
  KEY idx_media_asset_uploaded_by (uploaded_by),
  KEY idx_media_asset_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS system_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key VARCHAR(100) NOT NULL,
  config_value TEXT NULL,
  value_type VARCHAR(16) NOT NULL,
  description VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_system_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO cover_config (title, subtitle, background_image_url, avatar_image_url, links_json, is_active, created_at, updated_at)
SELECT 'Sam''s Blog', 'Writing, coding and life', NULL, NULL,
       JSON_ARRAY(JSON_OBJECT('label', 'Articles', 'url', '/articles', 'type', 'internal', 'sortOrder', 1)),
       1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cover_config WHERE is_active = 1);

INSERT INTO profile_config (display_name, bio, avatar_image_url, email, location, social_links_json, content_markdown, is_active, created_at, updated_at)
SELECT 'Sam Lee', 'Personal blog owner', NULL, NULL, 'Shanghai', JSON_ARRAY(), '## About me', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM profile_config WHERE is_active = 1);

INSERT INTO system_config (config_key, config_value, value_type, description, created_at, updated_at)
VALUES
  ('site.title', 'Sam''s Blog', 'STRING', 'Site title', NOW(), NOW()),
  ('site.description', 'Personal blog', 'STRING', 'Site description', NOW(), NOW()),
  ('comment.defaultStatus', 'VISIBLE', 'STRING', 'Default status for new comments', NOW(), NOW()),
  ('upload.maxFileSizeMb', '10', 'NUMBER', 'Maximum upload file size in MB', NOW(), NOW()),
  ('upload.allowedImageTypes', 'image/jpeg,image/png,image/webp,image/gif', 'STRING', 'Allowed image MIME types', NOW(), NOW())
ON DUPLICATE KEY UPDATE config_key = VALUES(config_key);
