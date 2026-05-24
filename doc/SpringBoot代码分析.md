# Spring Boot 代码分析文档

本文面向当前 `Blog-backend` 项目，按 Spring Boot 后端运行链路梳理每个相关文件的整体作用、主要类/函数及其职责。覆盖范围包括 Maven 配置、应用配置、数据库迁移脚本、`src/main/java` 下全部运行时代码，以及 `src/test/java` 下已有测试代码。

## 1. 项目整体结构

这是一个基于 Spring Boot 3.5.0 的个人博客后端，主要能力包括公开文章/项目展示、后台内容管理、游客注册登录与评论、图片上传、站点配置、Redis Token 登录态、Redis 缓存、MySQL 持久化和 Flyway 数据库迁移。

主要分层如下：

| 层/目录 | 作用 |
| --- | --- |
| `com.blog.BlogApplication` | 应用入口，启动 Spring Boot，启用异步能力和自定义配置绑定。 |
| `config` | Spring Security、MyBatis-Plus、静态资源映射、自定义配置属性。 |
| `common` | 统一响应、分页响应、业务异常、错误码、全局异常处理。 |
| `controller` | REST API 入口，负责路由、参数校验触发、响应包装。 |
| `service` | 业务逻辑、事务边界、缓存读写、文件存储、权限上下文使用。 |
| `mapper` | MyBatis-Plus 数据访问接口。 |
| `domain` | 数据库实体和枚举。 |
| `dto` | 请求参数模型，包含 Bean Validation 校验注解。 |
| `vo` | 响应视图模型，避免直接暴露实体。 |
| `security` | 当前登录用户模型、认证上下文、Token 认证过滤器。 |
| `util` | 通用工具，如 slug 生成、评论内容转义。 |
| `resources/db/migration` | Flyway 数据库版本迁移脚本。 |

典型请求链路：

1. 请求进入 Spring MVC。
2. `TokenAuthFilter` 从 `Authorization: Bearer ...` 读取 Token，并从 Redis 恢复登录用户。
3. `SecurityConfig` 判断接口是否公开、是否需要登录、是否需要管理员角色。
4. Controller 接收参数并触发 `@Valid`、`@Min`、`@Max` 等校验。
5. Service 执行业务逻辑、事务、缓存、文件操作、数据库访问。
6. Mapper 通过 MyBatis-Plus 操作 MySQL。
7. Controller 返回 `ApiResponse`，异常由 `GlobalExceptionHandler` 转成统一错误响应。

## 2. 构建与配置文件

### `pom.xml`

整体作用：Maven 构建文件，声明 Spring Boot 父工程、Java 版本、核心依赖和打包插件。

主要配置：

| 配置/依赖 | 作用 |
| --- | --- |
| `spring-boot-starter-parent:3.5.0` | 继承 Spring Boot 依赖管理和插件默认配置。 |
| `java.version=17` | 使用 Java 17，与 Spring Boot 3 生态匹配。 |
| `spring-boot-starter-web` | 提供 Spring MVC、嵌入式 Web 容器、JSON 序列化等能力。 |
| `spring-boot-starter-validation` | 提供 Jakarta Bean Validation，用于请求参数校验。 |
| `spring-boot-starter-security` | 提供认证、授权、Filter 链和权限异常处理能力。 |
| `spring-boot-starter-data-redis` | 访问 Redis，用于 Token、登录限流、接口缓存。 |
| `mybatis-plus-spring-boot3-starter` | 集成 MyBatis-Plus 与 Spring Boot 3。 |
| `mybatis-plus-jsqlparser` | 支持 MyBatis-Plus 分页插件等 SQL 解析能力。 |
| `flyway-core`、`flyway-mysql` | 管理数据库结构版本迁移。 |
| `mysql-connector-j` | MySQL JDBC 驱动。 |
| `lombok` | 为实体类生成 getter/setter 等样板代码。 |
| `spring-boot-starter-test` | JUnit、AssertJ 等测试依赖。 |
| `maven-compiler-plugin` | 配置 Lombok 注解处理器。 |
| `spring-boot-maven-plugin` | 支持 Spring Boot 应用打包，排除 Lombok 运行时依赖。 |

### `src/main/resources/application.yml`

整体作用：Spring Boot 应用运行配置，支持通过环境变量覆盖默认值。

主要配置：

| 配置项 | 作用 |
| --- | --- |
| `server.port` | HTTP 服务端口，默认 `8080`。 |
| `spring.application.name` | 应用名 `blog-backend`。 |
| `spring.datasource.*` | MySQL 连接地址、用户名、密码和驱动。 |
| `spring.data.redis.*` | Redis 主机、端口、密码和库编号。 |
| `spring.servlet.multipart.*` | 上传文件大小限制。 |
| `spring.flyway.enabled` | 启用 Flyway 自动迁移。 |
| `spring.flyway.baseline-on-migrate` | 对已有数据库执行 baseline 兼容。 |
| `mybatis-plus.global-config.banner` | 关闭 MyBatis-Plus banner。 |
| `mybatis-plus.configuration.map-underscore-to-camel-case` | 数据库下划线字段映射到 Java 驼峰字段。 |
| `app.upload-root` | 本地上传文件根目录，默认 `./data/uploads`。 |
| `app.token-ttl-seconds` | 登录 Token 有效期，默认 604800 秒。 |
| `app.admin-username`、`app.admin-password` | 启动时初始化管理员账号。 |

## 3. 应用入口与配置类

### `src/main/java/com/blog/BlogApplication.java`

整体作用：Spring Boot 主启动类。

主要类/函数：

| 类/函数 | 作用 |
| --- | --- |
| `BlogApplication` | 应用入口类，位于 `com.blog` 根包，使子包都能被组件扫描。 |
| `main(String[] args)` | 调用 `SpringApplication.run` 启动应用。 |
| `@SpringBootApplication` | 启用自动配置、组件扫描和配置类能力。 |
| `@EnableAsync` | 启用 `@Async` 异步方法，当前用于文章浏览量异步递增。 |
| `@EnableConfigurationProperties(AppProperties.class)` | 绑定 `app.*` 配置到 `AppProperties`。 |

### `src/main/java/com/blog/config/AppProperties.java`

整体作用：承载 `application.yml` 中 `app.*` 自定义配置。

主要类/字段：

| 类/字段 | 作用 |
| --- | --- |
| `AppProperties` | Java record，使用 `@ConfigurationProperties(prefix = "app")` 绑定配置。 |
| `uploadRoot` | 上传文件根目录。 |
| `tokenTtlSeconds` | Redis Token 过期时间。 |
| `adminUsername` | 启动初始化管理员用户名。 |
| `adminPassword` | 启动初始化管理员密码。 |

### `src/main/java/com/blog/config/MybatisPlusConfig.java`

整体作用：配置 MyBatis-Plus 分页插件和实体公共时间字段自动填充。

主要类/函数：

| 类/函数 | 作用 |
| --- | --- |
| `MybatisPlusConfig` | `@Configuration` 配置类。 |
| `mybatisPlusInterceptor()` | 注册 `MybatisPlusInterceptor`，并添加 MySQL 分页拦截器 `PaginationInnerInterceptor(DbType.MYSQL)`。 |
| `metaObjectHandler()` | 注册 `MetaObjectHandler`，统一填充实体时间字段。 |
| `insertFill(MetaObject)` | 插入时填充 `createdAt` 和 `updatedAt`。 |
| `updateFill(MetaObject)` | 更新时填充 `updatedAt`。 |

### `src/main/java/com/blog/config/SecurityConfig.java`

整体作用：配置 Spring Security 的无状态认证、接口权限规则、异常响应和密码编码器。

主要类/函数：

| 类/函数 | 作用 |
| --- | --- |
| `SecurityConfig` | `@Configuration` + `@EnableMethodSecurity` 安全配置类。 |
| `securityFilterChain(HttpSecurity, TokenAuthFilter, ObjectMapper)` | 定义安全过滤链。关闭 CSRF，启用无状态 Session，配置 URL 权限，并把 `TokenAuthFilter` 放到用户名密码过滤器前。 |
| `passwordEncoder()` | 注册 `BCryptPasswordEncoder`，用于用户密码哈希和校验。 |
| `writeError(...)` | 将认证失败和权限不足统一写成 `ApiResponse.error` JSON。 |

权限规则要点：

| 路径 | 权限 |
| --- | --- |
| `POST /api/auth/guest/register`、`POST /api/auth/login` | 公开。 |
| `GET /api/cover`、`/api/profile`、`/api/categories`、`/api/tags` | 公开。 |
| `GET /api/articles/**`、`GET /api/projects/**` | 公开。 |
| `/uploads/**` | 公开访问静态图片。 |
| `/api/admin/**` | 需要 `ADMIN` 角色。 |
| `/api/auth/logout`、`/api/auth/me` | 需要 `GUEST` 或 `ADMIN`。 |
| `POST /api/articles/*/comments` | 需要 `GUEST` 或 `ADMIN`。 |

### `src/main/java/com/blog/config/WebMvcConfig.java`

整体作用：把本地上传目录映射成 Web 静态资源。

主要类/函数：

| 类/函数 | 作用 |
| --- | --- |
| `WebMvcConfig` | 实现 `WebMvcConfigurer` 的配置类。 |
| 构造函数 | 注入 `AppProperties`，读取上传根目录。 |
| `addResourceHandlers(ResourceHandlerRegistry)` | 将 `/uploads/**` 映射到 `app.uploadRoot` 对应的本地目录。 |

## 4. 通用响应、错误与异常处理

### `src/main/java/com/blog/common/ApiResponse.java`

整体作用：统一 API 响应结构。

主要类/函数：

| 类/函数 | 作用 |
| --- | --- |
| `ApiResponse<T>` | record，字段为 `code`、`message`、`data`。 |
| `success(T data)` | 生成成功响应，`code=SUCCESS`、`message=success`。 |
| `error(ErrorCode code, String message)` | 生成失败响应，失败时 `data=null`。 |

### `src/main/java/com/blog/common/PageResponse.java`

整体作用：统一分页响应结构。

主要类/函数：

| 类/函数 | 作用 |
| --- | --- |
| `PageResponse<T>` | record，字段为 `items`、`page`、`size`、`total`、`pages`。 |
| `of(List<T>, long, long, long)` | 根据 `total` 和 `size` 自动计算总页数。 |

### `src/main/java/com/blog/common/ErrorCode.java`

整体作用：定义业务错误码和对应 HTTP 状态码。

主要枚举值：

| 枚举值 | HTTP 状态 | 作用 |
| --- | --- | --- |
| `SUCCESS` | 200 | 成功。 |
| `VALIDATION_ERROR` | 400 | 参数校验失败。 |
| `UNAUTHORIZED` | 401 | 未登录或认证失败。 |
| `FORBIDDEN` | 403 | 已登录但权限不足。 |
| `NOT_FOUND` | 404 | 资源不存在。 |
| `CONFLICT` | 409 | 资源冲突，如 slug 重复。 |
| `RATE_LIMITED` | 429 | 触发限流。 |
| `UPLOAD_ERROR` | 400 | 上传相关错误。 |
| `INTERNAL_ERROR` | 500 | 内部错误。 |

主要函数：

| 函数 | 作用 |
| --- | --- |
| `status()` | 返回该错误码对应的 `HttpStatus`。 |

### `src/main/java/com/blog/common/BizException.java`

整体作用：业务异常类型，用于 Service 主动抛出可预期错误。

主要类/函数：

| 类/函数 | 作用 |
| --- | --- |
| `BizException` | 继承 `RuntimeException`，携带 `ErrorCode`。 |
| 构造函数 | 传入错误码和错误消息。 |
| `getCode()` | 返回业务错误码，供全局异常处理器决定 HTTP 状态。 |

### `src/main/java/com/blog/common/GlobalExceptionHandler.java`

整体作用：统一捕获异常并返回 `ApiResponse`。

主要类/函数：

| 函数 | 作用 |
| --- | --- |
| `handleBiz(BizException)` | 按业务异常中的 `ErrorCode` 返回响应。 |
| `handleValidation(MethodArgumentNotValidException)` | 处理 `@RequestBody` 参数校验失败，拼接字段错误信息。 |
| `handleConstraint(ConstraintViolationException)` | 处理路径参数、查询参数上的校验失败。 |
| `handleUnauthorized(Exception)` | 处理认证失败，返回 `UNAUTHORIZED`。 |
| `handleForbidden(AccessDeniedException)` | 处理权限不足，返回 `FORBIDDEN`。 |
| `handleUploadSize(MaxUploadSizeExceededException)` | 处理超过 multipart 上传限制。 |
| `handleUnknown(Exception)` | 兜底处理未知异常，返回 `INTERNAL_ERROR`。 |

## 5. 安全认证模块

### `src/main/java/com/blog/security/LoginUser.java`

整体作用：表示 Redis 中保存和 Spring Security 上下文中使用的登录用户。

主要字段：

| 字段 | 作用 |
| --- | --- |
| `userId` | 用户 ID。 |
| `username` | 用户名。 |
| `nickname` | 昵称。 |
| `role` | 用户角色，`ADMIN` 或 `GUEST`。 |
| `loginAt` | 登录时间。 |
| `token` | 当前 Token 字符串。 |

### `src/main/java/com/blog/security/AuthContext.java`

整体作用：从 Spring Security 上下文获取当前登录用户。

主要类/函数：

| 函数 | 作用 |
| --- | --- |
| `currentUser()` | 读取 `SecurityContextHolder`，如果不存在 `LoginUser` 则抛出 `UNAUTHORIZED`。 |
| `currentUserId()` | 快捷返回当前登录用户 ID。 |

### `src/main/java/com/blog/security/TokenAuthFilter.java`

整体作用：自定义 Bearer Token 认证过滤器。

主要类/函数：

| 类/函数 | 作用 |
| --- | --- |
| `TOKEN_KEY_PREFIX` | Redis Token key 前缀：`auth:token:`。 |
| 构造函数 | 注入 `StringRedisTemplate` 和 `ObjectMapper`。 |
| `doFilterInternal(...)` | 从 `Authorization` 请求头解析 Bearer Token，到 Redis 查询登录用户 JSON，反序列化为 `LoginUser`，构造 `UsernamePasswordAuthenticationToken` 并写入 `SecurityContextHolder`。 |

认证流程：

1. 客户端携带 `Authorization: Bearer {token}`。
2. 过滤器查询 Redis key `auth:token:{token}`。
3. 查到后恢复 `LoginUser`。
4. 授权信息使用 `ROLE_` + 用户角色名，例如 `ROLE_ADMIN`。

## 6. Controller 接口层

### `src/main/java/com/blog/controller/AuthController.java`

整体作用：认证相关 API。

基础路径：`/api/auth`

主要类/函数：

| 函数 | 路径 | 作用 |
| --- | --- | --- |
| `registerGuest(...)` | `POST /guest/register` | 游客注册，调用 `AuthService.registerGuest`。 |
| `login(...)` | `POST /login` | 用户登录，调用 `AuthService.login`，返回 Token。 |
| `logout()` | `POST /logout` | 当前用户登出，删除 Redis Token。 |
| `me()` | `GET /me` | 返回当前登录用户基本信息。 |

### `src/main/java/com/blog/controller/PublicController.java`

整体作用：前台公开接口和登录后发表评论接口。

基础路径：`/api`

主要类/函数：

| 函数 | 路径 | 作用 |
| --- | --- | --- |
| `cover()` | `GET /cover` | 获取首页封面配置。 |
| `profile()` | `GET /profile` | 获取个人资料页配置。 |
| `articles(...)` | `GET /articles` | 分页查询已发布文章，支持分类、标签、关键词过滤。 |
| `articleDetail(String slug)` | `GET /articles/{slug}` | 根据 slug 获取已发布文章详情。 |
| `projects(...)` | `GET /projects` | 分页查询已发布项目，支持关键词过滤。 |
| `projectDetail(String slug)` | `GET /projects/{slug}` | 根据 slug 获取项目详情。 |
| `categories()` | `GET /categories` | 获取分类列表。 |
| `tags()` | `GET /tags` | 获取标签列表。 |
| `comments(...)` | `GET /articles/{slug}/comments` | 获取文章可见评论。 |
| `createComment(...)` | `POST /articles/{slug}/comments` | 登录用户发表评论。 |

### `src/main/java/com/blog/controller/AdminArticleController.java`

整体作用：后台文章管理接口。

基础路径：`/api/admin/articles`

主要类/函数：

| 函数 | 路径 | 作用 |
| --- | --- | --- |
| `list(...)` | `GET /` | 后台分页查询文章，支持状态、关键词、分类过滤。 |
| `create(...)` | `POST /` | 创建文章。 |
| `detail(Long id)` | `GET /{id}` | 根据 ID 获取文章详情。 |
| `update(Long id, ...)` | `PUT /{id}` | 更新文章。 |
| `delete(Long id)` | `DELETE /{id}` | 软删除文章。 |

### `src/main/java/com/blog/controller/AdminProjectController.java`

整体作用：后台项目展示管理接口。

基础路径：`/api/admin/projects`

主要类/函数：

| 函数 | 路径 | 作用 |
| --- | --- | --- |
| `list(...)` | `GET /` | 后台分页查询项目，支持状态和关键词过滤。 |
| `create(...)` | `POST /` | 创建项目。 |
| `detail(Long id)` | `GET /{id}` | 查询项目详情。 |
| `update(Long id, ...)` | `PUT /{id}` | 更新项目。 |
| `delete(Long id)` | `DELETE /{id}` | 软删除项目。 |

### `src/main/java/com/blog/controller/AdminCommentController.java`

整体作用：后台评论管理接口。

基础路径：`/api/admin/comments`

主要类/函数：

| 函数 | 路径 | 作用 |
| --- | --- | --- |
| `list(...)` | `GET /` | 分页查询评论，支持状态、文章 ID、用户名过滤。 |
| `updateStatus(Long id, ...)` | `PATCH /{id}/status` | 修改评论状态。 |
| `delete(Long id)` | `DELETE /{id}` | 软删除评论。 |

### `src/main/java/com/blog/controller/AdminMediaController.java`

整体作用：后台图片上传和媒体资源管理接口。

基础路径：`/api/admin/media/images`

主要类/函数：

| 函数 | 路径 | 作用 |
| --- | --- | --- |
| `upload(...)` | `POST /` | 上传图片，使用 `MultipartFile` 和 `MediaUsageType`。 |
| `list(...)` | `GET /` | 分页查询图片资源，可按用途过滤。 |
| `delete(Long id)` | `DELETE /{id}` | 软删除媒体记录。 |

### `src/main/java/com/blog/controller/AdminSiteController.java`

整体作用：后台站点配置管理接口。

基础路径：`/api/admin`

主要类/函数：

| 函数 | 路径 | 作用 |
| --- | --- | --- |
| `cover()` | `GET /cover` | 查询封面配置。 |
| `updateCover(...)` | `PUT /cover` | 更新封面配置。 |
| `profile()` | `GET /profile` | 查询个人资料配置。 |
| `updateProfile(...)` | `PUT /profile` | 更新个人资料配置。 |
| `configs()` | `GET /system-configs` | 查询系统配置列表。 |
| `updateConfig(...)` | `PUT /system-configs/{configKey}` | 更新指定系统配置。 |

### `src/main/java/com/blog/controller/AdminTaxonomyController.java`

整体作用：后台分类和标签管理接口。

基础路径：`/api/admin`

主要类/函数：

| 函数 | 路径 | 作用 |
| --- | --- | --- |
| `createCategory(...)` | `POST /categories` | 创建分类。 |
| `updateCategory(...)` | `PUT /categories/{id}` | 更新分类。 |
| `deleteCategory(Long id)` | `DELETE /categories/{id}` | 删除分类，Service 会校验是否被文章使用。 |
| `createTag(...)` | `POST /tags` | 创建标签。 |
| `updateTag(...)` | `PUT /tags/{id}` | 更新标签。 |
| `deleteTag(Long id)` | `DELETE /tags/{id}` | 删除标签，并清理文章标签关系。 |

### `src/main/java/com/blog/controller/AdminUserController.java`

整体作用：后台游客用户和管理员密码管理接口。

基础路径：`/api/admin`

主要类/函数：

| 函数 | 路径 | 作用 |
| --- | --- | --- |
| `guests(...)` | `GET /guests` | 分页查询游客用户。 |
| `resetPassword(...)` | `PATCH /guests/{id}/password` | 重置游客密码，并使其已有 Token 失效。 |
| `updateStatus(...)` | `PATCH /guests/{id}/status` | 更新游客状态，禁用时使其 Token 失效。 |
| `changePassword(...)` | `PATCH /me/password` | 当前管理员修改自己的密码。 |

## 7. Service 业务层

### `src/main/java/com/blog/service/AuthService.java`

整体作用：处理注册、登录、登出、Token 管理和登录失败限流。

主要类/函数：

| 函数/常量 | 作用 |
| --- | --- |
| `MAX_LOGIN_FAILURES` | 登录失败最大次数，当前为 5。 |
| `LOGIN_RATE_WINDOW` | 登录失败统计窗口，当前为 15 分钟。 |
| `USER_TOKEN_KEY_PREFIX` | 用户 Token 集合 key 前缀，用于批量失效。 |
| `registerGuest(...)` | 创建游客账号，校验用户名唯一，BCrypt 哈希密码。 |
| `login(...)` | 校验账号密码和状态；失败时写 Redis 失败计数；成功时生成 Token，写入 Redis 并返回登录视图。 |
| `logout(LoginUser)` | 删除当前 Token，并从用户 Token 集合移除。 |
| `invalidateUserTokens(Long userId)` | 删除某个用户的全部 Token，用于改密或禁用账号。 |
| `toUserView(User)` | 将实体转换为前端安全视图，不包含密码哈希。 |
| `findActiveByUsername(String)` | 查询未软删除用户。函数名中的 active 指未删除，状态是否 `ACTIVE` 在登录处另行判断。 |
| `userTokensKey(Long)` | 生成用户 Token 集合 Redis key。 |
| `clientIp(HttpServletRequest)` | 优先读取 `X-Forwarded-For`，否则读取远端地址。 |

Redis key 设计：

| Key | 作用 |
| --- | --- |
| `auth:token:{token}` | Token 到 `LoginUser` JSON 的映射。 |
| `auth:user:{userId}:tokens` | 某用户持有的 Token 集合。 |
| `rate:login:{username}:{ip}` | 登录失败计数。 |

### `src/main/java/com/blog/service/UserService.java`

整体作用：后台游客账号管理和管理员改密。

主要类/函数：

| 函数 | 作用 |
| --- | --- |
| `guests(...)` | 分页查询游客用户，可按用户名模糊搜索。 |
| `resetGuestPassword(...)` | 重置游客密码，并调用 `AuthService.invalidateUserTokens` 使旧登录态失效。 |
| `updateGuestStatus(...)` | 更新游客状态，禁用时清理登录态。 |
| `changeAdminPassword(...)` | 校验当前管理员旧密码，更新新密码，并清理该管理员全部 Token。 |
| `getGuest(Long)` | 获取未删除且角色为 `GUEST` 的用户，否则抛出 `NOT_FOUND`。 |
| `toGuestView(User)` | 转换游客列表视图。 |
| `baseGuestQuery()` | 查询游客的基础条件：`role=GUEST` 且未软删除。 |

### `src/main/java/com/blog/service/StartupInitializer.java`

整体作用：应用启动时自动初始化管理员账号。

主要类/函数：

| 类/函数 | 作用 |
| --- | --- |
| `StartupInitializer` | `@Component`，实现 `ApplicationRunner`。 |
| `run(ApplicationArguments)` | 启动后检查 `app.adminUsername` 是否已存在；不存在则创建 `ADMIN` 用户，密码使用 BCrypt 存储。 |

### `src/main/java/com/blog/service/ArticleService.java`

整体作用：文章公开查询、后台管理、分类标签关联、缓存和软删除。

主要类/函数：

| 函数 | 作用 |
| --- | --- |
| `publicList(...)` | 查询已发布文章列表。使用分类 slug、标签 slug、关键词构建查询条件；结果缓存到 Redis 5 分钟。 |
| `publicDetail(String slug)` | 查询已发布文章详情。优先读 Redis 详情缓存；每次访问异步增加浏览量。 |
| `adminList(...)` | 后台分页查询文章，支持状态、关键词、分类 ID 过滤。 |
| `adminDetail(Long id)` | 后台按 ID 查询文章详情。 |
| `create(...)` | 创建文章，校验分类/标签，生成 slug，设置发布时间和创建/更新人，保存文章并维护标签关系。 |
| `update(...)` | 更新文章，校验 slug 唯一，必要时设置首次发布时间，替换标签关系，清理缓存。 |
| `delete(Long id)` | 软删除文章，设置 `deletedAt`，清理缓存。 |
| `findPublishedBySlug(String)` | 查询已发布文章实体，给评论模块复用。 |
| `getExisting(Long id)` | 查询未删除文章，否则抛出 `NOT_FOUND`。 |
| `toSummaryView(Article)` | 转换文章摘要响应，包含分类和标签视图。 |
| `toDetailView(Article)` | 转换文章详情响应。 |
| `toMutationView(Article)` | 转换文章创建/更新后的简要响应。 |
| `categoryView(Long)` | 根据分类 ID 查询分类视图，分类删除或不存在时返回 `null`。 |
| `replaceTags(Long, List<Long>)` | 删除旧标签关系，再插入去重后的新关系。 |
| `ensureSlugAvailable(String, Long)` | 校验文章 slug 唯一，更新时排除自身。 |
| `publicArticleQuery(...)` | 构造公开文章查询条件：未删除、已发布、分类、标签、关键词。 |
| `baseArticleQuery()` | 基础查询条件：`deletedAt is null`。 |
| `evictArticleCaches(String)` | 删除文章详情缓存，并按模式删除文章列表缓存。 |

缓存 key：

| Key | 作用 |
| --- | --- |
| `article:list:{page}:{size}:{md5(filters)}` | 文章列表缓存。 |
| `article:detail:{slug}` | 文章详情缓存。 |

### `src/main/java/com/blog/service/ArticleViewCounterService.java`

整体作用：异步增加文章浏览量。

主要类/函数：

| 函数 | 作用 |
| --- | --- |
| `increment(Long articleId)` | 使用 `@Async` 异步调用 `ArticleMapper.incrementViewCount`，避免浏览详情接口被计数更新阻塞。 |

### `src/main/java/com/blog/service/CategoryService.java`

整体作用：分类公开列表和后台分类增删改。

主要类/函数：

| 函数 | 作用 |
| --- | --- |
| `publicList()` | 查询未删除分类，按 `sortOrder`、`id` 升序。 |
| `create(...)` | 创建分类，自动生成或规范化 slug，并校验唯一。 |
| `update(...)` | 更新分类信息和 slug。 |
| `delete(Long id)` | 删除分类前检查是否有未删除文章引用；有引用则抛 `CONFLICT`；否则软删除。 |
| `getExisting(Long id)` | 查询未删除分类。 |
| `toView(Category)` | 转换分类响应视图。 |
| `ensureSlugAvailable(String, Long)` | 校验分类 slug 唯一。 |
| `baseQuery()` | 基础查询条件：未软删除。 |

### `src/main/java/com/blog/service/TagService.java`

整体作用：标签公开列表、后台标签管理、文章标签关系查询。

主要类/函数：

| 函数 | 作用 |
| --- | --- |
| `publicList()` | 查询未删除标签，按名称升序。 |
| `findByArticleId(Long)` | 根据文章 ID 先查 `article_tag`，再查标签实体。 |
| `viewsByArticleId(Long)` | 查询文章标签并转换为视图。 |
| `ensureAllExist(Collection<Long>)` | 校验文章提交的标签 ID 都存在且未删除。 |
| `create(...)` | 创建标签，校验名称和 slug 唯一。 |
| `update(...)` | 更新标签名称和 slug。 |
| `delete(Long id)` | 软删除标签，并删除该标签的文章关联记录。 |
| `getExisting(Long id)` | 查询未删除标签。 |
| `toView(Tag)` | 转换标签响应视图。 |
| `ensureNameAndSlugAvailable(...)` | 校验名称或 slug 是否冲突。 |
| `baseQuery()` | 基础查询条件：未软删除。 |

### `src/main/java/com/blog/service/ProjectService.java`

整体作用：项目公开展示和后台项目管理。

主要类/函数：

| 函数 | 作用 |
| --- | --- |
| `publicList(...)` | 查询已发布项目，支持关键词过滤，按 `sortOrder` 和 ID 排序。 |
| `publicDetail(String slug)` | 根据 slug 查询已发布项目详情。 |
| `adminList(...)` | 后台分页查询项目，支持状态和关键词过滤。 |
| `adminDetail(Long id)` | 后台按 ID 查询项目详情。 |
| `create(...)` | 创建项目，生成 slug，写入 JSON 标签，记录创建/更新人。 |
| `update(...)` | 更新项目内容、状态、排序、标签等。 |
| `delete(Long id)` | 软删除项目。 |
| `getExisting(Long id)` | 查询未删除项目。 |
| `toView(Project)` | 转换项目视图，同时生成 `detailUrl=/projects/{slug}`。 |
| `ensureSlugAvailable(String, Long)` | 校验项目 slug 唯一。 |
| `resolveSlug(String, String)` | 使用请求 slug 或标题生成 slug，若为空抛校验错误。 |
| `writeTags(List<String>)` | 将标签列表序列化成 JSON 字符串。 |
| `readTags(String)` | 将 JSON 字符串反序列化成标签列表，失败返回空列表。 |
| `normalizeTags(List<String>)` | 过滤空标签、去首尾空格、去重。 |
| `baseQuery()` | 基础查询条件：未软删除。 |

### `src/main/java/com/blog/service/CommentService.java`

整体作用：文章评论公开查询、发表评论和后台评论管理。

主要类/函数：

| 函数 | 作用 |
| --- | --- |
| `publicComments(...)` | 根据文章 slug 查询已发布文章，再分页查询 `VISIBLE` 评论。 |
| `create(...)` | 登录用户发表评论；清洗评论 HTML；写入默认状态、IP 和 User-Agent。 |
| `adminList(...)` | 后台分页查询评论，支持状态、文章 ID、用户名过滤。 |
| `updateStatus(...)` | 更新评论状态。 |
| `delete(Long id)` | 软删除评论。 |
| `getExisting(Long id)` | 查询未删除评论。 |
| `toCommentView(Comment)` | 转换公开评论视图，包含作者信息。 |
| `toAdminView(Comment)` | 转换后台评论视图，包含文章标题、IP、UA。 |
| `defaultStatus()` | 从系统配置 `comment.defaultStatus` 读取新评论默认状态；缺省为 `VISIBLE`。 |
| `baseQuery()` | 基础查询条件：未软删除。 |
| `clientIp(HttpServletRequest)` | 获取客户端 IP。 |

### `src/main/java/com/blog/service/MediaService.java`

整体作用：图片上传、校验、本地存储、媒体记录管理。

主要类/函数：

| 函数/常量 | 作用 |
| --- | --- |
| `EXTENSIONS` | MIME 类型到扩展名映射，支持 jpeg/png/webp/gif。 |
| `upload(MultipartFile, MediaUsageType)` | 校验文件，生成 UUID 文件名，按用途和日期保存到本地目录，写入 `media_asset` 记录。 |
| `list(...)` | 分页查询未删除媒体资源，可按用途过滤。 |
| `delete(Long id)` | 软删除媒体记录。当前不删除物理文件。 |
| `validate(MultipartFile)` | 校验文件非空、MIME 类型、系统允许类型、大小限制、扩展名与 MIME 是否匹配。 |
| `allowedTypes()` | 从系统配置 `upload.allowedImageTypes` 读取允许 MIME 类型。 |
| `maxFileSizeMb()` | 从系统配置 `upload.maxFileSizeMb` 读取最大文件大小，默认 10MB。 |
| `safeFilename(String)` | 清理原始文件名中的路径分隔符和换行符。 |
| `toView(MediaAsset)` | 转换媒体资源响应视图。 |

存储路径示例：

```text
{app.uploadRoot}/{usageType.path()}/2026/05/19/{uuid}.jpg
/uploads/{usageType.path()}/2026/05/19/{uuid}.jpg
```

### `src/main/java/com/blog/service/SiteService.java`

整体作用：站点封面、个人资料、系统配置的查询和更新，并对公开配置做 Redis 缓存。

主要类/函数：

| 函数 | 作用 |
| --- | --- |
| `cover()` | 获取封面配置，优先读取 Redis `site:cover`，未命中则查数据库并缓存 30 分钟。 |
| `updateCover(...)` | 更新当前激活封面配置，并删除缓存。 |
| `profile()` | 获取个人资料配置，优先读取 Redis `site:profile`。 |
| `updateProfile(...)` | 更新当前激活个人资料配置，并删除缓存。 |
| `systemConfigs()` | 查询全部系统配置，按 key 排序。 |
| `updateSystemConfig(...)` | 更新指定系统配置。 |
| `activeCover()` | 查询 `isActive=true` 的封面配置。 |
| `activeProfile()` | 查询 `isActive=true` 的个人资料配置。 |
| `toCoverView(CoverConfig)` | 转换封面视图，并解析链接 JSON。 |
| `toProfileView(ProfileConfig)` | 转换个人资料视图，并解析社交链接 JSON。 |
| `toSystemConfigView(SystemConfig)` | 转换系统配置视图。 |
| `readLinks(String)` | 将 JSON 字符串解析成 `LinkItem` 列表，失败返回空列表。 |
| `writeJson(Object)` | 将链接列表等对象序列化为 JSON。 |
| `cache(String, Object)` | 将对象序列化后写入 Redis，TTL 为 30 分钟。 |

### `src/main/java/com/blog/service/SetUtils.java`

整体作用：Redis Set/key 工具类，目前用于按 pattern 删除缓存。

主要类/函数：

| 函数 | 作用 |
| --- | --- |
| `deleteByPattern(StringRedisTemplate, String)` | 使用 `redisTemplate.keys(pattern)` 找到匹配 key 并批量删除。当前用于删除 `article:list:*` 文章列表缓存。 |

## 8. Mapper 数据访问层

Mapper 全部使用 MyBatis-Plus `BaseMapper<T>`，基础 CRUD 和分页查询由框架提供。`@Mapper` 让 Spring/MyBatis 扫描并创建代理对象。

| 文件 | 整体作用 | 主要类/函数 |
| --- | --- | --- |
| `ArticleMapper.java` | 文章表访问。 | `ArticleMapper extends BaseMapper<Article>`；`incrementViewCount(Long id)` 使用 SQL 原子递增浏览量。 |
| `ArticleTagMapper.java` | 文章-标签关系表访问。 | `ArticleTagMapper extends BaseMapper<ArticleTag>`。 |
| `CategoryMapper.java` | 分类表访问。 | `CategoryMapper extends BaseMapper<Category>`。 |
| `CommentMapper.java` | 评论表访问。 | `CommentMapper extends BaseMapper<Comment>`。 |
| `CoverConfigMapper.java` | 首页封面配置表访问。 | `CoverConfigMapper extends BaseMapper<CoverConfig>`。 |
| `MediaAssetMapper.java` | 媒体资源表访问。 | `MediaAssetMapper extends BaseMapper<MediaAsset>`。 |
| `ProfileConfigMapper.java` | 个人资料配置表访问。 | `ProfileConfigMapper extends BaseMapper<ProfileConfig>`。 |
| `ProjectMapper.java` | 项目展示表访问。 | `ProjectMapper extends BaseMapper<Project>`。 |
| `SystemConfigMapper.java` | 系统配置表访问。 | `SystemConfigMapper extends BaseMapper<SystemConfig>`。 |
| `TagMapper.java` | 标签表访问。 | `TagMapper extends BaseMapper<Tag>`。 |
| `UserMapper.java` | 用户表访问。 | `UserMapper extends BaseMapper<User>`。 |

## 9. Domain 实体与枚举

### 实体类

实体类主要使用 Lombok `@Data` 生成 getter、setter、`equals`、`hashCode`、`toString` 等方法。使用 `@TableId(type = IdType.AUTO)` 标记自增主键，使用 `@TableField(fill = ...)` 配合 `MetaObjectHandler` 自动填充时间。

| 文件 | 整体作用 | 主要字段/说明 |
| --- | --- | --- |
| `Article.java` | 文章实体，对应 `article` 表。 | `id`、`title`、`slug`、`summary`、`coverImageUrl`、`contentMarkdown`、`contentHtml`、`categoryId`、`status`、`viewCount`、`publishedAt`、`createdBy`、`updatedBy`、`createdAt`、`updatedAt`、`deletedAt`。 |
| `ArticleTag.java` | 文章和标签多对多关系实体，对应 `article_tag` 表。 | `id`、`articleId`、`tagId`、`createdAt`。 |
| `Category.java` | 分类实体，对应 `category` 表。 | `id`、`name`、`slug`、`description`、`sortOrder`、`createdAt`、`updatedAt`、`deletedAt`。 |
| `Comment.java` | 评论实体，对应 `comment` 表。 | `id`、`articleId`、`userId`、`content`、`status`、`ipAddress`、`userAgent`、`createdAt`、`updatedAt`、`deletedAt`。 |
| `CoverConfig.java` | 首页封面配置实体，对应 `cover_config` 表。 | `title`、`subtitle`、`backgroundImageUrl`、`avatarImageUrl`、`linksJson`、`isActive`。 |
| `MediaAsset.java` | 媒体资源实体，对应 `media_asset` 表。 | `originalFilename`、`storedFilename`、`contentType`、`fileSize`、`storageType`、`storagePath`、`url`、`usageType`、`uploadedBy`、`createdAt`、`deletedAt`。 |
| `ProfileConfig.java` | 个人资料配置实体，对应 `profile_config` 表。 | `displayName`、`bio`、`avatarImageUrl`、`email`、`location`、`socialLinksJson`、`contentMarkdown`、`isActive`。 |
| `Project.java` | 项目展示实体，对应 `project` 表。 | `title`、`slug`、`description`、`contentMarkdown`、`imageUrl`、`projectUrl`、`tagsJson`、`sortOrder`、`status`、`createdBy`、`updatedBy`、`createdAt`、`updatedAt`、`deletedAt`。 |
| `SystemConfig.java` | 系统配置实体，对应 `system_config` 表。 | `configKey`、`configValue`、`valueType`、`description`、`createdAt`、`updatedAt`。 |
| `Tag.java` | 标签实体，对应 `tag` 表。 | `id`、`name`、`slug`、`createdAt`、`updatedAt`、`deletedAt`。 |
| `User.java` | 用户实体，对应反引号包裹的 `user` 表。 | `@TableName("`user`")` 避免 `user` 关键字冲突；字段包括 `username`、`nickname`、`passwordHash`、`role`、`status`、`lastLoginAt`、`createdAt`、`updatedAt`、`deletedAt`。 |

### 枚举类

| 文件 | 枚举值 | 作用 |
| --- | --- | --- |
| `ArticleStatus.java` | `DRAFT`、`PUBLISHED`、`HIDDEN` | 文章状态：草稿、已发布、隐藏。 |
| `CommentStatus.java` | `VISIBLE`、`HIDDEN`、`PENDING` | 评论状态：可见、隐藏、待审核。 |
| `MediaUsageType.java` | `ARTICLE`、`COVER`、`PROFILE`、`PROJECT`、`OTHER` | 图片用途。每个值绑定一个路径片段，`path()` 返回上传子目录。 |
| `ProjectStatus.java` | `DRAFT`、`PUBLISHED`、`HIDDEN` | 项目状态。 |
| `UserRole.java` | `ADMIN`、`GUEST` | 用户角色。 |
| `UserStatus.java` | `ACTIVE`、`DISABLED` | 用户状态。 |
| `ValueType.java` | `STRING`、`NUMBER`、`BOOLEAN`、`JSON` | 系统配置值类型。 |

## 10. DTO 请求模型

### `src/main/java/com/blog/dto/Requests.java`

整体作用：集中定义请求 DTO。使用 Java record 减少样板代码，使用 Bean Validation 注解约束输入。

主要内部 record：

| record | 作用 | 关键字段/校验 |
| --- | --- | --- |
| `GuestRegisterRequest` | 游客注册请求。 | `username` 必填、3-64 位、只能字母数字下划线横线；`password` 6-64 位；`nickname` 1-64 位。 |
| `LoginRequest` | 登录请求。 | `username`、`password` 必填。 |
| `ArticleRequest` | 文章创建/更新请求。 | `title` 必填；`slug`、`summary`、`coverImageUrl` 长度限制；`contentMarkdown` 必填；`categoryId`、`tagIds`、`status` 可选。 |
| `ProjectRequest` | 项目创建/更新请求。 | `title` 必填；项目说明、图片、链接、标签、排序、状态。 |
| `CategoryRequest` | 分类创建/更新请求。 | `name` 必填；`slug`、`description`、`sortOrder`。 |
| `TagRequest` | 标签创建/更新请求。 | `name` 必填；`slug` 可选。 |
| `CommentRequest` | 评论请求。 | `content` 必填，最多 2000 字符。 |
| `CommentStatusRequest` | 修改评论状态请求。 | `status` 必填。 |
| `GuestPasswordRequest` | 重置游客密码请求。 | `newPassword` 6-64 位。 |
| `GuestStatusRequest` | 修改游客状态请求。 | `status` 必填。 |
| `AdminPasswordRequest` | 管理员修改自己密码请求。 | `oldPassword`、`newPassword` 必填。 |
| `LinkItem` | 封面链接/社交链接条目。 | `label`、`url` 必填，`type`、`sortOrder` 可选。 |
| `CoverRequest` | 首页封面配置请求。 | `title` 必填，包含背景图、头像和链接列表。 |
| `ProfileRequest` | 个人资料配置请求。 | `displayName` 必填，包含简介、头像、邮箱、位置、社交链接、Markdown。 |
| `SystemConfigRequest` | 系统配置更新请求。 | `configValue`、`valueType`、`description`。 |

## 11. VO 响应模型

### `src/main/java/com/blog/vo/Views.java`

整体作用：集中定义 API 响应视图，避免把数据库实体直接暴露给前端。

主要内部 record：

| record | 作用 |
| --- | --- |
| `UserView` | 登录用户基础信息。 |
| `LoginView` | 登录成功响应，包含 Token、过期秒数和用户信息。 |
| `GuestView` | 后台游客列表项。 |
| `CategoryView` | 分类响应。 |
| `TagView` | 标签响应。 |
| `ArticleSummaryView` | 文章列表摘要视图，包含分类、标签、状态、浏览量和时间。 |
| `ArticleDetailView` | 文章详情视图，比摘要多 Markdown/HTML 正文。 |
| `ArticleMutationView` | 文章创建/更新后的简要响应。 |
| `ProjectView` | 项目展示视图，包含详情 URL、Markdown、标签、状态和排序。 |
| `AuthorView` | 评论作者信息。 |
| `CommentView` | 公开评论视图。 |
| `AdminCommentView` | 后台评论视图，包含文章标题、IP、UA。 |
| `CoverView` | 首页封面配置响应。 |
| `ProfileView` | 个人资料配置响应。 |
| `MediaAssetView` | 上传图片资源响应。 |
| `SystemConfigView` | 系统配置响应。 |

## 12. 工具类

### `src/main/java/com/blog/util/SlugUtil.java`

整体作用：把标题或用户输入转换为适合 URL 的 slug。

主要类/函数：

| 函数 | 作用 |
| --- | --- |
| `from(String value)` | 对字符串做 Unicode 规范化，去除音调，转小写，把非字母数字和非中文字符替换为 `-`，去除首尾 `-`。如果输入为空返回空字符串；如果清洗后为空，返回当前毫秒时间戳。 |

示例：

```text
First Article: Spring Boot! -> first-article-spring-boot
后端 实现 设计 -> 后端-实现-设计
```

### `src/main/java/com/blog/util/ContentSanitizer.java`

整体作用：清洗评论内容，降低 XSS 风险。

主要类/函数：

| 函数 | 作用 |
| --- | --- |
| `cleanComment(String content)` | 对评论做 `trim`，并使用 `HtmlUtils.htmlEscape` 转义 HTML；空值按空字符串处理。 |

## 13. 数据库迁移脚本

### `src/main/resources/db/migration/V1__init_schema.sql`

整体作用：初始化核心数据库结构和默认配置数据。

主要内容：

| 表/数据 | 作用 |
| --- | --- |
| `user` | 用户表，支持管理员和游客，包含唯一用户名、角色、状态、软删除字段。 |
| `category` | 分类表，包含 slug、排序和软删除字段。 |
| `tag` | 标签表，标签名和 slug 唯一。 |
| `article` | 文章表，保存标题、slug、摘要、封面、Markdown、状态、浏览量、发布时间和创建/更新人。 |
| `article_tag` | 文章标签关系表，限制 `(article_id, tag_id)` 唯一。 |
| `comment` | 评论表，保存内容、状态、IP、User-Agent。 |
| `cover_config` | 首页封面配置表，链接列表用 JSON 保存。 |
| `profile_config` | 个人资料配置表，社交链接用 JSON 保存。 |
| `media_asset` | 图片资源表，记录本地路径、公开 URL、用途和上传人。 |
| `system_config` | 系统配置表，保存评论默认状态、上传限制等配置。 |
| 默认数据 | 插入默认封面、默认个人资料、默认系统配置。 |

### `src/main/resources/db/migration/V2__add_project.sql`

整体作用：新增项目展示表。

主要内容：

| 表/字段 | 作用 |
| --- | --- |
| `project` | 保存项目标题、slug、描述、图片、项目链接、标签 JSON、排序、状态、创建/更新人、软删除字段。 |
| `uk_project_slug` | 保证项目 slug 唯一。 |
| `idx_project_status_sort` | 支持按状态和排序查询公开项目。 |

### `src/main/resources/db/migration/V3__add_project_content_markdown.sql`

整体作用：为项目表增加 Markdown 长正文。

主要内容：

| SQL | 作用 |
| --- | --- |
| `ALTER TABLE project ADD COLUMN content_markdown LONGTEXT NULL AFTER description` | 让项目详情可以保存长文本说明。 |

## 14. 测试文件

### `src/test/java/com/blog/common/ApiResponseTest.java`

整体作用：测试统一响应结构。

主要函数：

| 函数 | 作用 |
| --- | --- |
| `successUsesUnifiedShape()` | 验证 `ApiResponse.success(true)` 的 code、message、data 是否符合预期。 |

### `src/test/java/com/blog/common/PageResponseTest.java`

整体作用：测试分页响应页数计算。

主要函数：

| 函数 | 作用 |
| --- | --- |
| `calculatesPagesFromTotalAndSize()` | 验证 `total=5`、`size=2` 时 `pages=3`。 |

### `src/test/java/com/blog/util/ContentSanitizerTest.java`

整体作用：测试评论内容 HTML 转义。

主要函数：

| 函数 | 作用 |
| --- | --- |
| `escapesHtmlInCommentContent()` | 验证 `<script>` 会被转义成 HTML 实体。 |

### `src/test/java/com/blog/util/SlugUtilTest.java`

整体作用：测试 slug 生成规则。

主要函数：

| 函数 | 作用 |
| --- | --- |
| `buildsLowercaseDashSlug()` | 验证英文标题转小写横线 slug。 |
| `keepsChineseCharacters()` | 验证中文字符会保留并用横线分隔。 |
| `returnsFallbackForBlankInput()` | 验证空白输入返回空字符串。 |

### `src/test/java/com/blog/vo/ViewsSerializationTest.java`

整体作用：测试登录用户 JSON 序列化兼容性，保障 Redis Token 存储可反序列化。

主要函数：

| 函数 | 作用 |
| --- | --- |
| `loginUserRoundTripsForRedisTokenStorage()` | 将 `LoginUser` 序列化为 JSON 后再反序列化，验证关键字段保持一致。 |

## 15. 关键业务设计总结

### 15.1 统一响应与异常

所有 Controller 都返回 `ApiResponse`。业务异常通过 `BizException` 携带 `ErrorCode`，最后由 `GlobalExceptionHandler` 统一转换成 HTTP 状态码和 JSON 响应。这样前端只需要处理稳定的 `code/message/data` 结构。

### 15.2 Redis Token 登录态

项目没有使用服务端 Session，也没有使用 JWT，而是使用 Redis 不透明 Token：

1. 登录成功生成随机 Token。
2. Redis 保存 `auth:token:{token} -> LoginUser JSON`。
3. 请求时 `TokenAuthFilter` 从 Redis 恢复用户。
4. 改密、禁用用户时通过 `auth:user:{userId}:tokens` 批量删除 Token。

优点是服务端可以主动失效 Token；代价是每次认证需要访问 Redis。

### 15.3 角色权限模型

角色只有两类：

| 角色 | 权限 |
| --- | --- |
| `ADMIN` | 可访问 `/api/admin/**`，管理文章、项目、评论、用户、配置、媒体。 |
| `GUEST` | 可登录、查看自己信息、登出、发表评论。 |

公开内容接口对匿名用户开放。

### 15.4 软删除

文章、分类、标签、评论、媒体、项目、用户等使用 `deletedAt` 作为软删除字段。Service 中统一使用 `baseQuery()` 风格过滤未删除数据。软删除保留了恢复和审计空间，但要注意唯一索引会让已删除数据的 slug/name 仍然占用唯一值。

### 15.5 缓存策略

| 模块 | 缓存 |
| --- | --- |
| 文章列表 | `article:list:*`，缓存 5 分钟。 |
| 文章详情 | `article:detail:{slug}`，缓存 10 分钟。 |
| 首页封面 | `site:cover`，缓存 30 分钟。 |
| 个人资料 | `site:profile`，缓存 30 分钟。 |

文章创建、更新、删除时会清理详情缓存和列表缓存。封面/资料更新时清理对应缓存。

### 15.6 文件上传

图片上传由 `MediaService` 负责：

1. 校验 MIME 类型、扩展名、大小和系统允许类型。
2. 根据 `MediaUsageType` 和日期生成目录。
3. 使用 UUID 生成存储文件名。
4. 保存到本地磁盘。
5. 写入 `media_asset` 元数据。
6. 通过 `WebMvcConfig` 暴露 `/uploads/**` 静态访问路径。

### 15.7 数据库迁移

Flyway 会在应用启动时自动执行 `src/main/resources/db/migration` 下的迁移脚本。当前迁移从基础博客模型扩展到项目展示模型，体现了 schema 随业务演进的过程。
