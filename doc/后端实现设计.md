# 个人博客网站系统后端实现设计

## 1. 设计依据

本文档基于 `博客网站设计要求.md`、`API设计.md`、`数据模型设计.md` 编写，后端采用 Spring Boot + MySQL + Redis，部署采用 Docker。

实现以 REST API 为边界，第一版覆盖封面页、信息页、文章、分类、标签、评论、游客账号、管理员后台、图片上传和系统配置。

关于游客密码：早期需求中提到展示或查询游客密码，但 API 与数据模型已明确密码不明文存储、不在响应中返回。后端实现以安全约定为准，只支持管理员重置游客密码。

## 2. 技术选型

| 类别 | 选型 | 说明 |
| --- | --- | --- |
| Web 框架 | Spring Boot 3.x | REST API、参数校验、异常处理 |
| 安全 | Spring Security | Token 认证、角色鉴权 |
| 持久层 | MyBatis-Plus | CRUD、分页、软删除、复杂查询 |
| 数据库 | MySQL 8.x | 业务数据持久化 |
| 缓存 | Redis | 登录态、限流、热点数据缓存 |
| 迁移 | Flyway | 数据库版本管理与初始化数据 |
| 文件存储 | 本地挂载目录 | 第一版使用 Docker volume 持久化 |
| Markdown | commonmark-java + jsoup | 可选后端渲染与 HTML 清洗 |

## 3. 分层结构

```text
com.blog
├── BlogApplication
├── common
│   ├── ApiResponse / PageResponse
│   ├── BizException / ErrorCode
│   └── GlobalExceptionHandler
├── config
│   ├── SecurityConfig
│   ├── RedisConfig
│   └── WebMvcConfig
├── security
│   ├── TokenAuthFilter
│   ├── LoginUser
│   └── AuthContext
├── module
│   ├── auth
│   ├── article
│   ├── category
│   ├── tag
│   ├── comment
│   ├── site
│   ├── media
│   └── user
└── infra
    ├── storage
    ├── markdown
    └── cache
```

每个业务模块内部按 `controller`、`service`、`mapper`、`entity`、`dto`、`vo`、`convert` 组织。

## 4. 核心模块设计

### 4.1 认证与授权

- 游客注册：校验用户名唯一，密码使用 BCrypt 哈希后写入 `user` 表，角色为 `GUEST`。
- 登录：校验账号状态、密码和失败次数；成功后生成不透明随机 token，写入 Redis。
- Token Key：`auth:token:{token}`，TTL 7 天，Value 保存用户 ID、用户名、昵称、角色、登录时间。
- Token 索引：额外维护 `auth:user:{userId}:tokens`，用于修改密码后批量失效会话。
- 鉴权：`TokenAuthFilter` 解析 `Authorization: Bearer <token>`，加载 Redis 登录态并写入 Spring Security 上下文。
- 角色规则：`PUBLIC` 放行；`GUEST` 允许 `GUEST` 与 `ADMIN`；`ADMIN` 仅允许管理员。
- 登录限流：`rate:login:{username}:{ip}`，15 分钟内失败 5 次返回 `RATE_LIMITED`。

### 4.2 文章

- 前台列表仅查询 `PUBLISHED` 且未软删除文章，按 `published_at` 倒序。
- 后台列表可按状态、关键词、分类分页查询。
- 创建文章时：
  - `title`、`contentMarkdown` 必填。
  - `slug` 为空时由标题生成；冲突返回 `CONFLICT`。
  - 状态为空默认 `DRAFT`。
  - 状态为 `PUBLISHED` 时设置 `published_at`。
- 更新文章时，文章主表与 `article_tag` 关系必须在同一事务内完成。
- 从非发布状态变为 `PUBLISHED` 时设置首次发布时间；再次编辑不覆盖原发布时间。
- 删除文章采用软删除，并清理文章列表、详情缓存。
- 浏览量第一版采用异步数据库原子递增，避免阻塞详情接口。

### 4.3 分类与标签

- 分类、标签均支持后台创建、更新、软删除。
- `slug` 可自动生成，但必须全局唯一。
- 删除分类前检查是否存在未删除文章引用，存在则返回 `CONFLICT`。
- 删除标签时同步删除 `article_tag` 关系。
- 前台分类、标签列表只返回未删除数据。

### 4.4 评论

- 游客或管理员登录后可发表评论。
- 评论必须绑定已发布且未删除文章。
- 评论内容长度 1-2000，保存前做基础清洗，响应时不返回任何密码字段。
- 第一版新评论默认 `VISIBLE`，后续可通过 `comment.defaultStatus` 切换为 `PENDING`。
- 后台可分页查询评论，支持按状态、文章、用户名过滤。
- 隐藏评论只更新状态；删除评论采用软删除。

### 4.5 站点配置

- 封面配置读取 `cover_config` 中 `is_active = 1` 的记录。
- 信息页读取 `profile_config` 中 `is_active = 1` 的记录。
- `links_json`、`social_links_json` 在服务层转换为结构化 DTO。
- 后台更新封面后删除 `site:cover` 缓存。
- 后台更新信息页后删除 `site:profile` 缓存。
- 系统配置通过 `system_config` 管理，更新后删除 `site:config` 缓存。

### 4.6 图片上传

- 上传接口仅管理员可用。
- 校验项：文件非空、MIME 类型、扩展名、大小限制、`usageType` 合法。
- 存储路径：`/app/uploads/{usageTypePath}/yyyy/MM/dd/{uuid}.{ext}`。
- URL 规则：`/uploads/{usageTypePath}/yyyy/MM/dd/{uuid}.{ext}`。
- 保存流程：先写入物理文件，再写入 `media_asset`；数据库写入失败时删除已保存文件。
- 删除图片默认软删除数据库记录，可异步删除物理文件。
- 通过 `WebMvcConfig` 映射 `/uploads/**` 到本地上传目录。

## 5. 数据库实现

### 5.1 表与枚举

按 `数据模型设计.md` 建表，核心表包括：

- `user`
- `article`
- `category`
- `tag`
- `article_tag`
- `comment`
- `cover_config`
- `profile_config`
- `media_asset`
- `system_config`

枚举统一使用字符串存储：`UserRole`、`UserStatus`、`ArticleStatus`、`CommentStatus`、`MediaUsageType`。

### 5.2 通用字段

- `created_at`、`updated_at` 由 MyBatis-Plus 自动填充。
- `deleted_at` 用于软删除；查询默认过滤已删除数据。
- 管理员操作写入 `created_by`、`updated_by` 等审计字段。

### 5.3 事务边界

| 场景 | 事务要求 |
| --- | --- |
| 创建/更新文章 | 文章主表与标签关系同事务 |
| 删除标签 | 标签软删除与关系删除同事务 |
| 更新密码 | 密码哈希保存与 token 失效同事务后执行 |
| 更新站点配置 | 数据库更新成功后删除 Redis 缓存 |
| 图片上传 | 文件系统与数据库做补偿处理 |

## 6. Redis 设计

| 用途 | Key | TTL |
| --- | --- | --- |
| 登录态 | `auth:token:{token}` | 7 天 |
| 用户 token 索引 | `auth:user:{userId}:tokens` | 7 天 |
| 登录限流 | `rate:login:{username}:{ip}` | 15 分钟 |
| 文章详情 | `article:detail:{slug}` | 10 分钟 |
| 文章列表 | `article:list:{page}:{size}:{filterHash}` | 5 分钟 |
| 封面配置 | `site:cover` | 30 分钟 |
| 信息页配置 | `site:profile` | 30 分钟 |
| 系统配置 | `site:config` | 30 分钟 |

文章变更后删除对应详情缓存，并通过缓存版本号或前缀扫描清理列表缓存。配置变更后删除对应站点配置缓存。

## 7. 接口实现规范

- 所有接口统一返回 `ApiResponse<T>`。
- 分页统一返回 `PageResponse<T>`，页码从 1 开始。
- 请求 DTO 使用 Bean Validation 注解校验。
- 业务异常统一抛出 `BizException(ErrorCode, message)`。
- Controller 不编写业务逻辑，只做参数接收、鉴权注解和响应转换。
- Service 负责事务、权限补充校验、缓存清理和业务规则。
- Mapper 只负责数据访问，复杂筛选使用 XML 或 QueryWrapper 明确表达。

## 8. 安全设计

- 密码只在请求体中出现，数据库只保存 BCrypt 哈希。
- 任何响应不返回明文密码或 `password_hash`。
- 管理员只能重置游客密码，不允许查看游客原始密码。
- 上传文件同时校验 MIME、扩展名和文件头，禁止路径穿越。
- 评论内容做长度限制和基础 XSS 防护。
- 若后端渲染 Markdown，必须使用 jsoup 清洗 HTML 后再返回。
- 管理接口全部校验 `ADMIN` 角色。
- 全局异常不暴露堆栈、SQL、服务器路径等内部信息。

## 9. 初始化与部署

### 9.1 初始化数据

Flyway 初始化脚本应创建：

- 一个管理员账号，密码来自环境变量或启动脚本生成。
- 默认封面配置。
- 默认信息页配置。
- 基础系统配置项：站点标题、评论默认状态、上传大小、允许图片类型。

### 9.2 Docker 部署

建议提供 `docker-compose.yml`：

- `app`：Spring Boot 服务。
- `mysql`：MySQL 8.x，挂载数据卷。
- `redis`：Redis，挂载配置或使用默认配置。
- `uploads`：宿主机 `./data/uploads` 挂载到容器 `/app/uploads`。

核心环境变量：

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_DATA_REDIS_HOST
SPRING_DATA_REDIS_PORT
APP_UPLOAD_ROOT=/app/uploads
APP_ADMIN_USERNAME
APP_ADMIN_PASSWORD
```

## 10. 测试策略

| 类型 | 覆盖范围 |
| --- | --- |
| 单元测试 | slug 生成、密码校验、DTO 转换、上传校验 |
| Service 测试 | 文章发布、评论创建、配置更新、游客密码重置 |
| 集成测试 | 登录态、角色鉴权、分页查询、缓存失效 |
| API 测试 | 公开接口、管理员接口、错误码、响应结构 |
| 安全测试 | 密码不泄露、上传非法文件、XSS 内容、越权访问 |

## 11. 第一版实现顺序

1. 项目脚手架、全局响应、异常、配置、Flyway 初始化。
2. 用户、认证、Token 鉴权、登录限流。
3. 文章、分类、标签后台管理与前台查询。
4. 评论发布、评论列表、后台评论管理。
5. 封面、信息页、系统配置管理与缓存。
6. 图片上传、静态资源映射、媒体资源管理。
7. Docker Compose、初始化脚本、接口与集成测试。
