CREATE TABLE IF NOT EXISTS project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  slug VARCHAR(200) NOT NULL,
  description VARCHAR(1000) NULL,
  image_url VARCHAR(500) NULL,
  project_url VARCHAR(500) NULL,
  tags_json JSON NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted_at DATETIME NULL,
  UNIQUE KEY uk_project_slug (slug),
  KEY idx_project_status_sort (status, sort_order, id),
  KEY idx_project_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
