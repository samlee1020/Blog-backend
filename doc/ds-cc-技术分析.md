# Blog Backend 技术面试问答分析

> 本文档基于 Blog Backend 项目，模拟互联网公司后端面试场景，列出面试官可能依据此项目提出的问题，并附参考答案。问题分为"项目直接相关"和"延伸追问"两类，延伸问题覆盖项目中未使用但面试常考的技术点。

---

## 目录

1. [项目概览与架构设计](#1-项目概览与架构设计)
2. [Spring Boot & Spring 框架](#2-spring-boot--spring-框架)
3. [认证与授权 (Spring Security + Redis)](#3-认证与授权-spring-security--redis)
4. [数据库设计与 ORM (MySQL + MyBatis-Plus)](#4-数据库设计与-orm-mysql--mybatis-plus)
5. [缓存设计 (Redis)](#5-缓存设计-redis)
6. [并发与异步](#6-并发与异步)
7. [文件上传与静态资源](#7-文件上传与静态资源)
8. [安全防护](#8-安全防护)
9. [部署与运维 (Docker + Flyway)](#9-部署与运维-docker--flyway)
10. [测试与代码质量](#10-测试与代码质量)
11. [开放性问题](#11-开放性问题)

---

## 1. 项目概览与架构设计

### Q1: 介绍一下你这个博客后端项目。

**参考答案：**

这是一个基于 Spring Boot 3 + MyBatis-Plus + MySQL + Redis 的个人博客后端，分为前台展示和后台管理两大场景。前台提供文章列表/详情、项目展示、分类标签、评论等公开 API；后台提供文章/项目/分类/标签/评论/用户/媒体/站点配置的管理 API。

技术选型上：
- Spring Security + Redis 实现无状态 Token 认证，支持管理员和游客双角色
- MyBatis-Plus 作为 ORM，LambdaQueryWrapper 构建查询
- Redis 同时承担登录态存储、登录限流、热点数据缓存
- Flyway 管理数据库版本演进
- Docker Compose 编排 MySQL、Redis、后端应用

### Q2: 项目的分层结构是怎样的？为什么这样分？

**参考答案：**

```
controller → service → mapper → domain
     ↕           ↕
   dto/vo     common/security/util
```

- **Controller**：只做路由、参数接收、校验触发、响应包装，保持薄层
- **Service**：承载核心业务逻辑、事务边界、缓存读写、权限上下文读取
- **Mapper**：基于 MyBatis-Plus BaseMapper，数据访问，少量自定义 SQL
- **Domain**：数据库实体与枚举，字段映射表结构
- **DTO/VO**：请求和响应模型与实体隔离，防止敏感字段泄露、接口与数据库解耦
- **Common**：统一响应、统一异常、错误码枚举
- **Security**：认证过滤器、登录用户模型、上下文工具
- **Config**：Spring Security、MyBatis-Plus、静态资源映射等配置

这样分的好处是：每层职责单一，业务规则集中在 Service，不会被 Controller 或 Mapper 打散；VO 隔离实体，可以按接口需要组装字段而不影响数据库模型。

### Q3: 为什么选择 MyBatis-Plus 而不是 JPA？两者有什么区别？

**参考答案：**

选择 MyBatis-Plus 的原因：
- 对 SQL 有完全控制力，复杂查询不会产生 Hibernate 那种不可控的 SQL
- LambdaQueryWrapper 提供了类型安全的条件构造，同时保持 SQL 透明
- 分页插件、自动填充、逻辑删除等开箱即用

JPA vs MyBatis-Plus 核心区别：

| 维度 | JPA/Hibernate | MyBatis-Plus |
|------|--------------|--------------|
| 编程模型 | 先设计对象关系映射，自动生成 SQL | 手动控制 SQL，框架辅助 |
| SQL 控制力 | SQL 自动生成，调优需学习 JPQL/HQL | 完全掌控 SQL |
| 关联查询 | @OneToMany/@ManyToOne 自动联表 | 需手动编写联表或多次查询组装 |
| 缓存 | 一级/二级缓存内置 | 需自行集成（本项目用 Redis） |
| 学习曲线 | 陡峭（懒加载、N+1、持久化上下文） | 平缓 |
| 适用场景 | 对象模型复杂、以聚合根操作为主 | SQL 密集、报表、复杂查询场景 |

**延伸问题：JPA 的 N+1 问题是什么？怎么解决？**

JPA 的 N+1 问题：查询主实体时（1 次 SQL），遍历访问关联实体时又各自发起查询（N 次 SQL）。例如查询 10 篇文章，每篇访问作者时再查一次，共 11 次查询。

解决方案：
- `@EntityGraph` 指定关联属性一起加载
- JPQL 中使用 `JOIN FETCH`
- `@BatchSize` 批量加载
- 直接写原生 SQL 或使用 DTO 投影

### Q4: 项目的请求 DTO 和响应 VO 都用 Java record，为什么？有什么优劣？

**参考答案：**

优点：
- 不可变对象，线程安全
- 自动生成构造器、getter、equals、hashCode、toString
- 语义明确，一看就知道是数据载体
- 配合 Jackson 序列化/反序列化无缝工作

劣势：
- 不能继承（但可以实现接口）
- 没有无参构造器（Jackson 通过 `@JsonCreator` 处理，实际上 Jackson 2.12+ 原生支持 record）
- 所有字段在构造时确定，不支持逐步设值（对 DTO/VO 这不是问题）

本项目中的实践：`Requests.java` 和 `Views.java` 将所有请求/响应 record 集中定义，清晰展示了每个接口的入参和出参结构。

---

## 2. Spring Boot & Spring 框架

### Q5: Spring Boot 的自动配置原理是什么？

**参考答案：**

Spring Boot 通过 `@SpringBootApplication` 注解中的 `@EnableAutoConfiguration` 触发自动配置：

1. `@EnableAutoConfiguration` 导入 `AutoConfigurationImportSelector`
2. 该 Selector 读取 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件（Spring Boot 3.x 新格式，旧版在 `spring.factories`）
3. 每个自动配置类使用 `@ConditionalOnClass`、`@ConditionalOnBean`、`@ConditionalOnProperty` 等条件注解判断是否生效
4. 例如 `DataSourceAutoConfiguration`：检测到 classpath 有 DataSource 类且没有用户自定义的 DataSource bean 时，根据 `spring.datasource.*` 配置自动创建

**延伸问题：如何自定义一个 Starter？**

步骤：
1. 创建 autoconfigure 模块，包含自动配置类（`@Configuration` + 条件注解）
2. 在 `AutoConfiguration.imports` 中声明自动配置类
3. 创建 starter 模块，仅包含 pom 依赖（把 autoconfigure 和需要的库传递进来）
4. 可选：通过 `@ConfigurationProperties` 暴露用户可配置项
5. 命名规范：官方用 `xxx-spring-boot-starter`，第三方用 `xxx-spring-boot-starter`

### Q6: `@Transactional` 放在 Service 层的方法上，什么情况下会失效？

**参考答案：**

常见失效场景：

1. **同类内部调用**：同一个类中方法 A（无事务）调用方法 B（有事务），由于 Spring AOP 代理机制，调用走的是 `this.B()` 而不是代理对象，事务不生效
2. **方法非 public**：Spring 默认使用 JDK 动态代理或 CGLIB，非 public 方法不被代理
3. **异常被 catch 吞掉**：事务只在 RuntimeException 和 Error 时回滚（默认），如果 catch 了异常没有继续抛出或没有手动 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`，事务不会回滚
4. **数据库引擎不支持事务**：MySQL MyISAM 引擎不支持事务
5. **多线程场景**：事务上下文通过 ThreadLocal 传递，新线程没有事务
6. **`@Transactional` 注解的 propagation 设置不当**：比如 `Propagation.NOT_SUPPORTED` 会挂起当前事务

本项目中的处理：Service 方法上使用 `@Transactional`，事务边界覆盖文章+标签关系、评论+用户关联等需要一致性的操作。

### Q7: Spring IOC 容器中 Bean 的生命周期是怎样的？

**参考答案：**

简要版（面试够用）：

1. **实例化**：通过构造器或工厂方法创建 Bean 实例
2. **属性填充**：依赖注入（`@Autowired`、`@Value` 等）
3. **Aware 回调**：`BeanNameAware` → `BeanFactoryAware` → `ApplicationContextAware`
4. **前置处理**：`BeanPostProcessor.postProcessBeforeInitialization()`
5. **初始化**：`@PostConstruct` → `InitializingBean.afterPropertiesSet()` → `init-method`
6. **后置处理**：`BeanPostProcessor.postProcessAfterInitialization()`（AOP 代理在此阶段生成）
7. **Bean 就绪**：可以使用
8. **销毁**：`@PreDestroy` → `DisposableBean.destroy()` → `destroy-method`

**延伸问题：BeanFactory 和 ApplicationContext 的区别？**

- BeanFactory 是底层容器，提供基本 DI 功能，延迟加载
- ApplicationContext 继承 BeanFactory，增加了国际化、事件发布、AOP、Web 上下文等企业级功能，默认预初始化单例 Bean

### Q8: Spring MVC 处理一个 HTTP 请求的完整流程是什么？

**参考答案：**

1. 请求到达 **DispatcherServlet**
2. DispatcherServlet 通过 **HandlerMapping** 找到对应的 Controller 方法
3. 通过 **HandlerAdapter** 调用 Controller 方法（参数解析、类型转换、校验）
4. Controller 返回逻辑视图名或 `@ResponseBody` 数据
5. **ViewResolver** 解析视图（对于 REST API，`@ResponseBody` 跳过这步）
6. **HttpMessageConverter** 将返回值序列化为 JSON（Jackson）
7. 响应返回客户端

在这个过程中：
- **拦截器 (Interceptor)**：在 HandlerMapping 之后、Controller 之前执行 preHandle；Controller 后执行 postHandle；视图渲染后执行 afterCompletion
- **过滤器 (Filter)**：在 DispatcherServlet 之前，是 Servlet 层面的拦截
- 本项目中的 `TokenAuthFilter` 就是 Filter 层，在请求到达 Controller 前完成认证

---

## 3. 认证与授权 (Spring Security + Redis)

### Q9: 你项目为什么用 Redis Token 而不是 JWT？优缺点是什么？

**参考答案：**

选择 Redis 不透明 Token 的核心原因：**需要服务端主动失效能力**。

具体场景：
- 管理员重置用户密码 → 用户所有旧 Token 立即失效
- 管理员禁用用户 → 该用户所有 Token 立即失效
- 管理员修改自己密码 → 自己所有旧 Token 立即失效
- 用户主动退出登录 → 当前 Token 立即失效

JWT 天然是无状态的，签发后无法撤销，除非引入黑名单（还是要查 Redis/DB），那不如直接用 Redis 存登录态。

| 维度 | Redis Token | JWT |
|------|------------|-----|
| 主动失效 | 原生支持，删 key 即可 | 需额外黑名单 |
| 性能 | 每次请求查 Redis | 本地验签，不依赖外部 |
| 水平扩展 | 依赖 Redis 可用性 | 无状态，天然支持 |
| Token 包含信息 | 只有随机字符串 | 可携带用户信息、权限 |
| 存储压力 | Redis 存所有登录态 | 无服务端存储 |

**延伸问题：如果让你设计一个同时使用两者的方案？**

可以用"短 TTL 的 access token (JWT) + 长 TTL 的 refresh token (Redis 不透明)"：
- Access Token：JWT，5-15 分钟过期，本地验签不查 Redis
- Refresh Token：Redis 不透明 Token，用于续期 Access Token，可主动失效
- 这样既减少了 Redis 访问频率，又保留了主动失效能力

### Q10: 你项目的 `TokenAuthFilter` 是怎么工作的？它在 Spring Security 链中的位置是什么？

**参考答案：**

工作流程：
1. 请求到达 → `TokenAuthFilter.doFilterInternal()` 被调用
2. 从 `Authorization` 请求头提取 `Bearer <token>`
3. 用 `auth:token:{token}` 作为 key 查询 Redis
4. 若查到，将 JSON 反序列化为 `LoginUser` 对象
5. 创建 `UsernamePasswordAuthenticationToken`，权限为 `ROLE_ADMIN` 或 `ROLE_GUEST`
6. 写入 `SecurityContextHolder`，后续授权判断可用

在 Security 链中的位置：
- 通过 `http.addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class)` 注册
- 它在 Spring 内置的用户名密码过滤器之前执行
- 但仍在 `SecurityContextHolderFilter` 之后

**延伸问题：OncePerRequestFilter 和普通 Filter 的区别？**

`OncePerRequestFilter` 保证在一次请求中只执行一次。在 Servlet 容器中，一个请求可能经过多个 Filter 链（比如 forward、include），普通 Filter 可能被多次执行。`OncePerRequestFilter` 通过 request 属性标记判断是否已处理，避免重复认证。

### Q11: 你的登录失败限流是怎么实现的？如果 Redis 挂了怎么办？

**参考答案：**

限流实现：
- Key: `rate:login:{username}:{clientIp}`
- 登录失败时，`INCR` 该 key，设置 15 分钟 TTL
- 每次登录先检查计数，达到 5 次则返回 `RATE_LIMITED`（429）
- 登录成功后删除该 key

这是一个固定窗口限流算法，简单有效但存在临界问题（窗口边界大量请求可能突破限制）。

Redis 挂了的情况：
- 认证链路：当前代码中 Redis 查询 `auth:token:{token}` 失败无法通过认证，用户被拒绝访问 → **fail-close 策略**
- 限流：Redis 不可用时 `opsForValue().get()` 会抛出异常 → 当前没有降级处理
- 缓存：直接从数据库查询，性能下降但功能不中断

生产优化方案：
- Redis 使用主从 + 哨兵/Cluster 提高可用性
- 限流可以降级为本地限流（Guava RateLimiter/Caffeine）
- 关键路径对 Redis 操作加超时和异常处理

**延伸问题：你还知道哪些限流算法？**

| 算法 | 原理 | 特点 |
|------|------|------|
| 固定窗口 | 统计周期内请求数 | 简单，但有临界问题 |
| 滑动窗口 | 基于时间窗口滚动计数 | 更平滑，Redis 可用 sorted set 或 hash 实现 |
| 漏桶 | 固定速率处理请求 | 强制平滑，适合保护下游 |
| 令牌桶 | 固定速率放令牌，请求取令牌 | 允许突发，更灵活 |
| 滑动日志 | 记录每次请求时间戳 | 最精确，但存储开销大 |

### Q12: Spring Security 中 `hasRole("ADMIN")` 和 `hasAuthority("ROLE_ADMIN")` 有什么区别？

**参考答案：**

- `hasRole("ADMIN")`：Spring Security 会自动加 `ROLE_` 前缀，内部判断的是 `ROLE_ADMIN`
- `hasAuthority("ROLE_ADMIN")`：直接比对完整的权限字符串，不带前缀
- 本项目中在 `TokenAuthFilter` 构造 `UsernamePasswordAuthenticationToken` 时，传入的 authority 是 `ROLE_ADMIN` 格式，所以 `hasRole("ADMIN")` 和 `hasAuthority("ROLE_ADMIN")` 都能匹配

**延伸问题：Spring Security 的认证流程是怎样的？**

核心链路：
1. `AbstractAuthenticationProcessingFilter` (如 UsernamePasswordAuthenticationFilter) 从请求提取凭据
2. 创建 `Authentication` 对象（未认证状态）
3. 调用 `AuthenticationManager` → `ProviderManager` → 遍历 `AuthenticationProvider`
4. 匹配的 Provider（如 `DaoAuthenticationProvider`）完成认证，返回已认证的 Authentication
5. 认证成功 → `SecurityContextHolder` 设置上下文 → 可选的 `RememberMeServices` → 触发 `AuthenticationSuccessHandler`
6. 认证失败 → `SecurityContextHolder` 清空 → 触发 `AuthenticationFailureHandler`

---

## 4. 数据库设计与 ORM (MySQL + MyBatis-Plus)

### Q13: 你的文章和标签是多对多关系，你是怎么设计的？为什么不直接在文章里存标签列表？

**参考答案：**

设计方式：使用中间表 `article_tag`（`article_id` + `tag_id`），并在 `(article_id, tag_id)` 上加唯一索引。

不用直接存标签列表的原因（规范化 vs 反规范化）：

| 维度 | 中间表 (规范化) | JSON 字段存标签 (反规范化) |
|------|---------------|--------------------------|
| 按标签筛选文章 | 一次 JOIN 即可 | 需 LIKE 或 JSON 函数查询，无法使用索引 |
| 标签重命名 | 修改一处，所有关联文章自动体现 | 需更新所有包含该标签的文章 |
| 删除标签清理关联 | 删除关系表数据即可 | 需更新所有文章 JSON |
| 写入性能 | 需要操作关系表 | 只需更新文章一行 |
| 数据分析 | 方便统计标签下文章数 | 较难 |

本项目中文章用中间表（因为按标签筛选是核心查询），项目用 JSON 字段 `tags_json`（因为项目标签更多是展示，无筛选和统计需求）。

**延伸问题：多对多中间表要不要加自增 ID？**

两种设计：
- `PRIMARY KEY (article_id, tag_id)`：简洁，天然保证唯一
- `PRIMARY KEY (id)` + `UNIQUE (article_id, tag_id)`：多一个 id 字段，好处是 ORM 框架支持更好、分页更方便、个别场景可以直接引用中间表 ID

本项目选择了后者，更适配 MyBatis-Plus 的 `IdType.AUTO` 机制。

### Q14: 你的软删除是怎么实现的？有什么需要注意的问题？

**参考答案：**

实现方式：每张需要软删除的表加 `deleted_at DATETIME NULL` 字段，删除时设当前时间而非物理删除。MyBatis-Plus 也支持 `@TableLogic` 注解自动过滤，但本项目手动控制。

所有 Service 通过 `baseQuery()` 方法统一过滤 `deleted_at IS NULL`。

需要注意的问题：

1. **唯一索引冲突**：`slug` 字段有 UNIQUE 约束，文章 A 被软删除后，不能创建同 slug 的文章 B → 解决方案：用联合唯一 `(slug, deleted_at)`，但 MySQL 中 NULL 不参与唯一判断，需要把 `deleted_at` 改为 `0`（未删除）和毫秒时间戳（已删除）

2. **级联软删除**：删除分类时，文章不级联删除（先检查引用），但删除文章时评论是否级联软删除要明确定义

3. **关联查询**：所有 JOIN 都记得加 `deleted_at IS NULL`

4. **恢复操作**：需要考虑是否支持"反删除"

5. **外键约束**：软删除了父记录，子记录的外键引用可能报错

**延伸问题：MyBatis-Plus 的逻辑删除 (`@TableLogic`) 原理？**

在实体 `deletedAt` 字段加 `@TableLogic(value = "0", delval = "now()")` 后：
- 自动在 SELECT 的 WHERE 加 `deleted_at = 0`
- DELETE 操作自动转为 UPDATE `SET deleted_at = now()`
- 需配合 `MetaObjectHandler` 处理更新时间

### Q15: 你项目里的分页是怎么实现的？MyBatis-Plus 分页的原理是什么？

**参考答案：**

本项目使用 MyBatis-Plus 的 `Page<T>` 对象 + `PaginationInnerInterceptor` 拦截器实现分页。

```
// Service 层
Page<Article> page = new Page<>(pageNum, pageSize);
articleMapper.selectPage(page, queryWrapper);
// 获取结果
page.getRecords();  // 数据列表
page.getTotal();    // 总记录数
```

原理：
1. 配置 `PaginationInnerInterceptor(DbType.MYSQL)` 注册到 MyBatis-Plus 拦截器链
2. `selectPage` 调用时，拦截器拦截 `Executor.query()`
3. 判断传入参数中有 `Page` 对象，识别为分页查询
4. 先执行 COUNT SQL（去掉 SELECT 列，替换为 `SELECT COUNT(*)`），获取 total
5. 再执行原始 SQL，自动拼接 `LIMIT offset, size`
6. 将结果和 total 写入 Page 对象返回

**延伸问题：深分页问题怎么解决？**

当 `LIMIT 1000000, 20` 时，MySQL 需要扫描前 100 万行再丢弃，性能很差。

解决方案：
- **游标分页**：用 `WHERE id > lastId ORDER BY id LIMIT 20` 代替 OFFSET，适合滚动加载
- **子查询优化**：`SELECT * FROM article WHERE id >= (SELECT id FROM article ORDER BY id LIMIT 1000000, 1) LIMIT 20`
- **覆盖索引**：只查索引字段，减少回表（但最终还是要回表取完整数据）
- **禁止跳页**：移动端常见方案，只支持上一页/下一页

### Q16: 你的文章发布状态和首次发布时间是怎么设计的？

**参考答案：**

- `ArticleStatus` 枚举：`DRAFT` → `PUBLISHED` → `HIDDEN`
- `publishedAt`：首次发布时设置，后续编辑不再覆盖
- 逻辑：创建或更新时，如果新状态是 `PUBLISHED` 且当前 `publishedAt` 为空，才设置首次发布时间
- 草稿/隐藏 → 首次发布：设置 `publishedAt`
- 已发布 → 已发布编辑：保持 `publishedAt` 不变
- 已发布 → 下架 → 重新发布：取决于产品口径，本项目中保留原有 `publishedAt`（因为是同一篇文章的首次发布时间）

```java
// 关键逻辑
if (newStatus == ArticleStatus.PUBLISHED && article.getPublishedAt() == null) {
    article.setPublishedAt(LocalDateTime.now());
}
```

### Q17: 你使用 Flyway 做数据库迁移，它的原理是什么？和 Liquibase 有什么区别？

**参考答案：**

Flyway 原理：
1. 启动时连接数据库，检查是否存在 `flyway_schema_history` 表
2. 不存在则创建该表
3. 扫描 `classpath:db/migration/` 下的迁移脚本，按版本号排序（`V1__xxx.sql`, `V2__xxx.sql`）
4. 与 `flyway_schema_history` 对比，执行未执行过的脚本
5. 每个脚本执行成功后，在 history 表中记录版本号、脚本名、checksum、执行时间
6. 任何脚本的 checksum 变化会报错，保证迁移不可篡改

| 维度 | Flyway | Liquibase |
|------|--------|-----------|
| 迁移格式 | SQL（推荐）/ Java | XML/YAML/JSON/SQL |
| 学习成本 | 低，就是写 SQL | 较高，需学习 DSL |
| 回滚 | 商业版支持 | 原生支持 rollback |
| 分支合并 | 用 out-of-order 处理 | 有 changeSet ID 机制 |
| 适用团队 | DBA 友好，直接写 SQL | DevOps 友好，多数据库兼容 |

本项目用 Flyway 是因为足够简单，MySQL 独占不需要跨库兼容。

---

## 5. 缓存设计 (Redis)

### Q18: 你的文章列表和文章详情是怎么做缓存的？用的什么缓存策略？

**参考答案：**

采用 **Cache-Aside（旁路缓存）** 模式：

**读流程：**
1. 先查 Redis，命中直接返回
2. 未命中，查数据库
3. 将结果写入 Redis（带 TTL），返回

**写流程：**
1. 先更新数据库
2. 删除（或更新）对应缓存

本项目中的实现：

| 数据 | Key | TTL | 失效时机 |
|------|-----|-----|---------|
| 文章列表 | `article:list:{page}:{size}:{filterHash}` | 5 分钟 | 文章创建/更新/删除时按 `article:list:*` 批量删除 |
| 文章详情 | `article:detail:{slug}` | 10 分钟 | 对应 slug 的文章变更时删除 |
| 封面配置 | `site:cover` | 30 分钟 | 更新封面配置时删除 |
| 个人资料 | `site:profile` | 30 分钟 | 更新资料配置时删除 |

文章列表 key 使用 `filterHash`（分类、标签、关键词组合的 MD5），避免 key 过长。

**延伸问题：Cache-Aside 模式有什么数据不一致的风险？**

经典并发问题：
1. 缓存过期 → 请求 A 查数据库（读到旧值） → 请求 B 更新数据库 + 删除缓存 → 请求 A 把旧值写入缓存
2. 结果：缓存中是旧数据，下次失效前读到都是旧值

解决方案：
- **延迟双删**：更新 DB 前删一次缓存，更新后再 sleep 几百毫秒删一次
- **队列串行化**：对同一 key 的读写请求排队
- **设置合理 TTL**：即使不一致也最终过期，对非强一致性场景可接受（本项目属于这类）

### Q19: 你的文章缓存失效用了 `KEYS article:list:*`，这有什么问题？怎么优化？

**参考答案：**

`KEYS` 命令的问题：
- Redis 是单线程执行命令，`KEYS` 会扫描整个键空间
- 当 key 数量很大时，会阻塞 Redis，导致所有请求排队等待
- 生产环境应禁用 `KEYS`

优化方案：
1. **SCAN 命令**：游标迭代，每次返回部分 key，不会阻塞 Redis
   ```java
   // SetUtils 可以改为
   try (var cursor = redisTemplate.scan(ScanOptions.scanOptions()
           .match("article:list:*").count(100).build())) {
       cursor.forEachRemaining(key -> keys.add(key));
   }
   redisTemplate.delete(keys);
   ```

2. **缓存版本号**：用 `article:list:version` 维护一个递增整数，文章变更时递增版本号
   ```
   Key: article:list:v{version}:{page}:{size}:{hash}
   ```
   - 无需删除旧缓存，自然被替代
   - 旧版本缓存等 TTL 自动过期

3. **Redis Cluster 哈希标签**：确保版本号 key 和数据 key 在同一个 slot，保证原子性

小项目用 `KEYS` 可以，但面试时要主动说你知道这个问题的存在和优化方案。

### Q20: 你的项目里登录态和热点数据都存在 Redis 中，如果 Redis 数据满了怎么办？

**参考答案：**

Redis 内存淘汰策略（`maxmemory-policy`）：

| 策略 | 行为 | 适用场景 |
|------|------|---------|
| `noeviction` | 不淘汰，写入报错 | 不允许数据丢失 |
| `allkeys-lru` | 所有 key 中淘汰最近最少用的 | 通用缓存 |
| `allkeys-lfu` | 所有 key 中淘汰最不常用的 | 热点与冷数据差异大 |
| `volatile-lru` | 带 TTL 的 key 中淘汰 LRU | 持久 key 不能丢 |
| `volatile-ttl` | 带 TTL 的 key 中淘汰最快过期的 | 本项目推荐 |

对本项目的建议：
- 登录态 token 不能丢（否则用户被踢下线），适合 `volatile-ttl`：优先过期 TTL 更短的数据
- 如果有持久化数据，用 `volatile-lru`
- 对于缓存（文章列表/详情），丢就丢了，回源数据库
- 最好对不同类型的 key 分开 Redis 实例或使用不同的淘汰策略

**延伸问题：Redis 集群的几种模式？**

- **主从**：一主多从，读写分离，手动切换
- **哨兵 (Sentinel)**：自动故障转移，在主从基础上加了监控和自动切换
- **Cluster**：数据分片，每个节点存一部分数据，支持横向扩展
- 本项目单机 Redis，适合个人项目，生产建议至少主从+哨兵

### Q21: 缓存穿透、缓存击穿、缓存雪崩分别是什么？你怎么防范？

**参考答案：**

| 问题 | 定义 | 本项目处理 | 通用方案 |
|------|------|-----------|---------|
| **缓存穿透** | 查询不存在的数据，缓存没有，每次都穿透到 DB | slug 查不到返回 404，但没有缓存"不存在" → 有穿透风险 | 缓存空值（短 TTL）+ 布隆过滤器 |
| **缓存击穿** | 热点 key 过期瞬间，大量请求同时打到 DB | 文章详情是热点，当前无特殊处理 | 互斥锁/分布式锁重建缓存 + 逻辑过期 |
| **缓存雪崩** | 大量 key 同时过期，DB 瞬间压力过大 | TTL 固定，有风险 | TTL 加随机值（如 10min ± 2min）+ 多级缓存 |

**缓存空值方案示例：**
```java
// 查询不存在的数据时，也写入缓存，value 为特殊标记
if (article == null) {
    redisTemplate.opsForValue().set(key, "NULL", Duration.ofMinutes(1));
}
```

**缓存击穿的互斥锁方案：**
```java
String lockKey = "lock:article:" + slug;
if (redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10))) {
    try {
        // 查 DB + 写缓存
    } finally {
        redisTemplate.delete(lockKey);
    }
} else {
    Thread.sleep(100); // 等待重建
    // 重试读缓存
}
```

---

## 6. 并发与异步

### Q22: 你项目的浏览量统计是怎么实现的？为什么要异步？

**参考答案：**

实现方式：
1. `ArticleViewCounterService.increment(articleId)` 方法标注 `@Async`
2. 调用 `articleMapper.incrementViewCount(id)`
3. SQL：`UPDATE article SET view_count = view_count + 1 WHERE id = #{id}`

异步的原因：
- 文章详情是高频读接口，浏览量更新不是响应内容的关键字段
- 同步更新会增加 DB 写操作延迟，拖慢详情接口
- 异步后详情接口只需读缓存/DB + 发一个异步任务，不等待更新完成

**为什么 SQL 用 `view_count = view_count + 1` 而不是先查再加？**

因为先查再加存在并发丢失更新问题：
```
线程 A: SELECT view_count → 100
线程 B: SELECT view_count → 100  (同时读到 100)
线程 A: UPDATE SET view_count = 101
线程 B: UPDATE SET view_count = 101  (丢失了 A 的更新！)
```
用 `view_count = view_count + 1` 是原子操作，由数据库行锁保证。

**延伸问题：`@Async` 的原理和注意事项？**

原理：
- `@EnableAsync` 启用异步支持
- Spring 创建代理，拦截 `@Async` 方法调用
- 提交给 `TaskExecutor` 在线程池中执行
- 默认使用 `SimpleAsyncTaskExecutor`（每次新建线程，不推荐生产使用）

注意事项：
1. 必须自定义线程池，配置核心线程数、最大线程数、队列大小、拒绝策略
2. 同类内部调用不走代理，`@Async` 失效（和 `@Transactional` 一样的问题）
3. 异步方法不能是 private
4. 返回值可以是 `void` 或 `Future<T>`/`CompletableFuture<T>`
5. 异常不会被调用方捕获，需自定义 `AsyncUncaughtExceptionHandler`

### Q23: `@Async` 和消息队列有什么区别？什么场景该用消息队列？

**参考答案：**

| 维度 | @Async | 消息队列 |
|------|--------|---------|
| 可靠性 | 进程内，服务挂了任务丢失 | 消息持久化，服务重启不丢 |
| 消费确认 | 无（最多抛异常日志） | 支持 ACK，消费失败可重试 |
| 顺序性 | 依赖线程池调度 | 分区内有序 |
| 扩展性 | 单进程内，不可独立扩容 | 消费者可独立伸缩 |
| 运维 | 无额外组件 | 需要部署 MQ 中间件 |

用消息队列的场景：
- 需要任务持久化和重试（下单后发短信）
- 需要削峰填谷（秒杀场景）
- 需要解耦多个下游（文章发布后通知搜索索引、邮件订阅、数据统计等多个服务）
- 需要最终一致性保证的异步任务

本项目浏览量统计对可靠性要求不高（丢了就少了几个浏览计数），用 `@Async` 足够了。如果未来要做评论审核通知、文章订阅推送等，可以考虑 RabbitMQ / Kafka。

---

## 7. 文件上传与静态资源

### Q24: 你的文件上传是怎么设计的？安全方面做了哪些考虑？

**参考答案：**

上传流程：
1. 校验文件非空、MIME 类型（image/jpeg、image/png、image/webp、image/gif）
2. MIME 类型还要在系统配置 `upload.allowedImageTypes` 白名单中
3. 校验文件大小（不超过系统配置 `upload.maxFileSizeMb`，默认 10MB）
4. 校验扩展名与 MIME 类型是否匹配
5. 清理原始文件名（移除路径分隔符、换行符等危险字符）
6. 服务端生成 UUID 作为存储文件名
7. 按 `{usageType}/{yyyy}/{MM}/{dd}/{uuid}.{ext}` 路径存储
8. 先写文件，再写 `media_asset` 记录；DB 写入失败则删除已落盘文件做补偿

安全措施：
- 不在存储路径中使用用户文件名，防止路径穿越
- MIME 白名单 + 大小限制
- 原始文件名只做元数据存储和展示

当前不足：
- 只校验 Content-Type（可伪造），没有读文件魔数
- 删除时只软删除数据库记录，不删物理文件
- 本地存储不支持多实例共享

**延伸问题：如果文件上传量很大，本地存储有什么问题？对象存储怎么选？**

本地存储的问题：
- 单机容量有限
- 多实例部署需要共享存储或同步
- 备份和容灾需要额外方案

对象存储 (OSS/S3/MinIO)：
- AWS S3：国际业务，生态最完善
- 阿里云 OSS：国内首选，CDN 加速方便
- MinIO：私有部署，兼容 S3 API，适合数据敏感场景

接入对象存储的核心改动：
- 用户上传 → 后端生成预签名 URL → 前端直传 OSS（减少后端带宽压力）
- 或者后端代理上传（控制力更强）
- 返回 OSS CDN URL 给前端

---

## 8. 安全防护

### Q25: 你的评论 XSS 防护是怎么做的？还有哪些常见的 Web 安全漏洞需要注意？

**参考答案：**

评论防护：使用 `HtmlUtils.htmlEscape()` 对用户输入做 HTML 实体转义，`<script>alert(1)</script>` 被转为 `&lt;script&gt;alert(1)&lt;/script&gt;`，浏览器不会执行。

常见 Web 安全漏洞：

| 漏洞 | 本项目状态 | 应对措施 |
|------|-----------|---------|
| **XSS** | 评论已做 escape，但文章内容未处理 | 前端渲染 Markdown 时做 sanitize |
| **CSRF** | 使用 Bearer Token 无状态认证，天然免疫 | 如果改用 Cookie 存 Token 需要加 CSRF 防护 |
| **SQL 注入** | MyBatis-Plus 参数化查询，无拼接 SQL | 避免 `${}` 占位符，只用 `#{}` |
| **路径穿越** | 上传用 UUID 作为文件名 | `Path.of(filename).getFileName()` 提取基础名 |
| **暴力破解** | 登录限流 5 次/15 分钟 | 可加强为验证码 + IP 黑名单 |
| **越权 (IDOR)** | 管理接口统一 `/api/admin/**` 权限控制 | 需注意同一角色内的横向越权（如游客 A 删游客 B 的评论） |
| **敏感信息泄露** | 异常处理不暴露堆栈，VO 不包含密码 | 生产配置不提交代码仓库 |

**延伸问题：如果后端要渲染 Markdown 为 HTML，怎么防止 XSS？**

Markdown 本身允许 HTML 混写，需要：
1. 使用支持 HTML 过滤的 Markdown 解析器（如 flexmark-java + HTML sanitizer）
2. 或者先渲染 Markdown 为 HTML，再用 OWASP Java HTML Sanitizer 做白名单过滤
3. 白名单规则：允许 p、h1-h6、a、img、code、pre、ul、ol、li 等排版标签，禁止 script、iframe、object 等

### Q26: 你的 IP 获取是从 `X-Forwarded-For` 取的，这有什么安全问题？

**参考答案：**

```java
String ip = request.getHeader("X-Forwarded-For");
if (ip == null || ip.isEmpty()) {
    ip = request.getRemoteAddr();
} else {
    ip = ip.split(",")[0].trim(); // 取第一个 IP
}
```

问题：
- `X-Forwarded-For` 可以被客户端伪造
- 攻击者可以构造任意 IP 来绕过 IP 限流
- 在没有反向代理时，不应该信任这个头

解决方案：
- 确保 Nginx/网关正确设置了 `X-Forwarded-For`（覆盖客户端传的值）
- 或者使用 `X-Real-IP`（Nginx 设置，只一个 IP）
- Spring Security 有 `ForwardedHeaderFilter` 可以协助处理
- 最安全的方式：只在已知的反向代理 IP 范围内信任该头

### Q27: BCrypt 为什么比 MD5/SHA 更适合存密码？还有什么密码存储方案？

**参考答案：**

| 算法 | 特点 | 适合密码? |
|------|------|----------|
| MD5 | 快速，易彩虹表碰撞 | 不适合 |
| SHA-256 | 快速，可 GPU 并行爆破 | 不适合 |
| SHA-256 + Salt | 防彩虹表，但仍然快速 | 不够好 |
| BCrypt | 慢哈希，内置盐，cost factor 可调 | 适合 |
| Argon2 | 更现代的内存硬函数，抗 GPU/ASIC | 最推荐 |
| SCrypt | 内存硬函数，类似 Argon2 | 适合 |

BCrypt 的优势：
- **内置盐值**：同一个密码每次 hash 结果不同，天然防彩虹表
- **慢哈希**：cost factor 设为 10 时，每个 hash 约 0.1 秒，暴力破解成本极高
- **不可逆**：无法从 hash 反推原文
- **无法 GPU 加速**：BCrypt 需要频繁访问内存，GPU 的并行优势不明显

Spring Security 默认推荐 BCrypt，本项目的 cost factor 是 10（默认值）。

---

## 9. 部署与运维 (Docker + Flyway)

### Q28: 你的 Dockerfile 用了多阶段构建，为什么？有什么好处？

**参考答案：**

```dockerfile
# 阶段1：构建
FROM maven:3.9-eclipse-temurin-17 AS build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

# 阶段2：运行
FROM eclipse-temurin:17-jre
COPY --from=build /app/target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

好处：
1. **镜像体积小**：运行镜像只含 JRE，不含 Maven、JDK、源码、依赖缓存
2. **安全**：减少攻击面（不含编译工具和源码）
3. **构建缓存**：Docker 分层缓存，pom.xml 不变时不重新下载依赖
4. **构建环境隔离**：不污染本地 Maven 仓库

**延伸问题：JRE 和 JDK 镜像的区别？**

- JDK: Java Development Kit，包含 JRE + 编译器 (javac) + 调试工具
- JRE: Java Runtime Environment，只含运行环境
- 生产运行只需要 JRE，体积比 JDK 小很多

### Q29: Docker Compose 中你怎么做健康检查和服务依赖的？

**参考答案：**

```yaml
mysql:
  healthcheck:
    test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
    interval: 10s
    timeout: 5s
    retries: 5

app:
  depends_on:
    mysql:
      condition: service_healthy
    redis:
      condition: service_healthy
```

- `depends_on` + `condition: service_healthy`：确保 MySQL 和 Redis 的健康检查通过后，才启动 app 服务
- 注意 `depends_on` 只控制启动顺序，不保证应用层面的就绪（MySQL 启动完成 ≠ 数据库可接受连接）
- 本项目设了端口绑定 `127.0.0.1:3306:3306`，防止数据库直接暴露公网

**延伸问题：`docker-compose up -d` 发生了什么？**

1. 读取 `docker-compose.yml`
2. 创建网络（如未指定则创建默认 bridge 网络）
3. 创建 volumes（如声明）
4. 按依赖顺序拉取/构建镜像
5. 按依赖顺序启动容器
6. `-d` 表示后台运行

### Q30: 项目启动时 Flyway 执行失败了怎么办？怎么排查和修复？

**参考答案：**

排查：
1. 查看 `flyway_schema_history` 表，找到最后成功执行的版本
2. 对比 checksum：已执行脚本的 checksum 是否与当前文件一致（不一致说明脚本被修改过）
3. 查看应用启动日志，找到失败的 SQL 和错误信息

常见失败场景及修复：

| 场景 | 修复 |
|------|------|
| checksum 不匹配 | 确认脚本确实是合理修改后，可以更新 checksum 或删除该条 history 重新执行 |
| SQL 语法错误 | 修复 SQL，删除 history 中该版本记录，重新启动 |
| 迁移脚本未执行 | 检查文件命名（`V{version}__{description}.sql`），版本号不能小于已执行的版本 |
| baseline 问题 | 已有数据库但 Flyway 报找不到历史表，设置 `baseline-on-migrate=true` |

**注意**：生产环境不要直接在 `flyway_schema_history` 表中手动删除，应该用 `flyway repair`（商业版）或评估影响后操作。

---

## 10. 测试与代码质量

### Q31: 你项目的测试覆盖了哪些？如果让你补全测试，你会怎么补？

**参考答案：**

现有测试（5 个纯单元测试）：
- `ApiResponseTest`：验证统一响应结构
- `PageResponseTest`：验证分页计算
- `ContentSanitizerTest`：验证 HTML escape
- `SlugUtilTest`：验证 slug 生成
- `ViewsSerializationTest`：验证 LoginUser JSON 序列化

补全计划：

**单元测试补充：**
- Service 层：Mock Mapper 和 Redis，测业务逻辑、边界条件、异常路径

**集成测试补充 (Testcontainers + MockMvc)：**
- 认证链路：登录 → 获取 Token → 带 Token 请求 → 验证返回
- 权限规则：游客访问 /api/admin/** → 403
- 文章 CRUD：创建 → 查询 → 更新 → 删除 → 验证缓存
- 评论发表：登录游客发表评论，验证内容被 escape
- 文件上传：正常上传、超大小、非法类型

**环境测试：**
- 启动时 Flyway 迁移正常
- 所有 Mapper 查询 SQL 语法正确
- Redis 连接可用时的缓存读写

**安全测试：**
- 未登录访问需要认证的接口返回 401
- 游客访问管理接口返回 403
- 登录限流触发后返回 429

### Q32: 你的项目中用了大量 Lombok，它有什么优缺点？面试官可能问什么？

**参考答案：**

优点：
- 减少样板代码（getter/setter/toString/equals/hashCode/构造器）
- 代码更简洁易读
- 修改字段时不会忘记更新相关方法

缺点：
- 需要 IDE 插件支持（否则报红）
- 调试时看不到生成的方法
- `@Data` 生成的 equals/hashCode 对所有字段敏感，双向关联可能导致栈溢出
- 团队新成员需要额外学习
- `@Builder` 对继承不友好

面试追问：
- **`@Data` 和 `@Value` 的区别？** `@Data` 生成可变的 getter/setter，`@Value` 生成不可变对象
- **Lombok 怎么处理的编译器？** 通过 JSR 269 注解处理器，在编译阶段操作 AST 生成代码

---

## 11. 开放性问题

### Q33: 如果这个博客要支撑日活 10 万，你觉得哪些地方需要改造？

**参考答案：**

**架构层面：**
1. **Web 层水平扩展**：当前单实例，加负载均衡 + 多实例
2. **Redis 升级**：单机 → Sentinel/Cluster，避免单点
3. **MySQL 读写分离**：主库写 + 从库读，文章列表和详情走从库
4. **静态资源分离**：上传文件迁移到对象存储 + CDN
5. **搜索引擎**：文章全文搜索用 Elasticsearch 替代 MySQL LIKE

**性能层面：**
1. N+1 查询优化：批量查询分类/标签/用户，用 Map 组装
2. 缓存优化：`KEYS` 换 `SCAN` 或版本号，加本地缓存 (Caffeine)
3. 文章列表缓存预热：热门查询提前写入缓存
4. 限流从单机 IP 限流升级为全局限流

**安全层面：**
1. 接入 API 网关做统一限流、鉴权、日志
2. 添加 CORS 配置或前端同域部署
3. 开启 HTTPS
4. 文件上传加文件头魔数校验 + 图片二次处理（去 EXIF + 压缩 + 生成 WebP/缩略图）

**可观测性：**
1. 接入 Spring Boot Actuator + Prometheus + Grafana 做监控
2. 关键操作加审计日志
3. 接入链路追踪 (SkyWalking/Jaeger)

### Q34: 如果你的前端同事说 "接口太慢了"，你怎么排查和优化？

**参考答案：**

**排查步骤：**
1. **复现**：确认哪个接口慢、什么参数下慢、是否稳定复现
2. **日志**：如果能加日志，记录 Controller 层和 Service 层耗时（目前项目没有日志，这是问题）
3. **慢 SQL**：MySQL 开启 `slow_query_log`，检查是否有全表扫描、缺少索引
4. **缓存命中率**：Redis 检查 key 是否存在、TTL 是否合理
5. **网络**：检查客户端到服务端的网络延迟

**本项目常见慢查询和优化：**

| 问题 | 优化 |
|------|------|
| 评论列表每行查用户信息（N+1） | 批量查用户，Map 组装 |
| 分类列表和文章列表分开请求 | 前端合并或后端联表一次性返回 |
| 文章列表无缓存穿透保护 | 缓存空值 |
| 浏览量异步但线程池太小导致排队 | 增大线程池 |
| 上传大图前端不做压缩 | 前端 Canvas 压缩后再上传 |

**如果没有时间做深度优化：**
- 先加缓存（文章列表/详情已有缓存，检查是否命中）
- 加索引（检查 `EXPLAIN` 执行计划，确保走索引）
- 限制分页大小（防止 `LIMIT 0, 100000`）
- 异步化非关键路径（如浏览量已经是异步）

### Q35: 如果让你从零重新设计这个博客后端，你会做哪些不同的技术选型或架构决策？

**参考答案：**

可以考虑的改变：

1. **数据库**：如果内容结构和查询更灵活（如多维分类、全文搜索），可以考虑 PostgreSQL（原生 JSONB、全文索引）

2. **ORM**：如果数据模型复杂且以聚合根操作为主，可以考虑 JPA + QueryDSL；如果继续 MyBatis-Plus，会提前写好联表查询 XML，避免 N+1

3. **认证**：访问量上来了可以 JWT (access token) + Redis (refresh token) 组合，减少 Redis 查询频率

4. **API 文档**：接入 SpringDoc / Knife4j 自动生成 API 文档

5. **缓存**：用 Caffeine 做本地一级缓存 + Redis 二级缓存，减少网络开销

6. **Markdown 渲染**：后端完成 Markdown → HTML（用 flexmark 或 commonmark-java + OWASP sanitizer），前端直接展示，减少前端渲染差异

7. **评论系统**：接入第三方评论系统（如 Giscus/ utterances）或考虑 WebSocket 做实时评论

8. **监控**：从第一天就接入 Actuator + Micrometer，至少能看到 JVM 内存、GC、请求延迟、缓存命中率

9. **日志**：统一用 Slf4j + Logback，关键操作打日志

10. **代码结构**：按业务域拆分包（article/comment/user/site），而不是按技术层，更 DDD 友好

### Q36: 你的项目中没有使用到的，但面试常问的 Spring Boot 面试题有哪些？

**以下是"即使项目没用，面试也要会"的知识点：**

**Q: Spring 事务的传播行为有哪些？**

| 传播行为 | 描述 |
|---------|------|
| REQUIRED (默认) | 有事务则加入，无则新建 |
| REQUIRES_NEW | 总是新建事务，挂起当前 |
| NESTED | 嵌套事务，内层回滚不影响外层 |
| SUPPORTS | 有事务则加入，无则非事务执行 |
| NOT_SUPPORTED | 以非事务执行，挂起当前 |
| NEVER | 以非事务执行，有事务则抛异常 |
| MANDATORY | 必须有事务，否则抛异常 |

**Q: Spring Bean 的作用域有哪几种？**

| 作用域 | 描述 |
|--------|------|
| singleton (默认) | 容器内单例 |
| prototype | 每次获取新建实例 |
| request | 每个 HTTP 请求一个实例 |
| session | 每个 HTTP 会话一个实例 |
| application | 每个 ServletContext 一个实例 |

**Q: Spring Boot 中如何实现定时任务？**

使用 `@EnableScheduling` + `@Scheduled`：
- `@Scheduled(fixedRate = 5000)`：固定频率，从任务开始时计时
- `@Scheduled(fixedDelay = 5000)`：固定延迟，从任务结束时计时
- `@Scheduled(cron = "0 0 2 * * ?")`：Cron 表达式，每天凌晨 2 点

分布式定时任务需要加锁（如 ShedLock、XXL-Job、Elastic-Job）。

**Q: Spring Boot 应用如何做优雅关闭？**

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

原理：
1. 收到 SIGTERM 后，拒绝新请求
2. 等待已接收的请求处理完成（不超过 timeout）
3. 销毁 Bean，释放资源

**Q: Spring 中如何保证接口幂等性？**

| 方案 | 适用场景 |
|------|---------|
| 数据库唯一约束 | INSERT 重复时忽略 |
| Token 机制 | 前端先获取 Token，提交时校验一次性 Token |
| 乐观锁 (version 字段) | UPDATE 时 `WHERE version = oldVersion` |
| 分布式锁 (Redis) | 同一操作不并发执行 |
| 状态机 | 已支付订单不可再支付 |

---

## 总结：面试答辩思路

面试中介绍这个项目时，建议按以下节奏：

1. **一句话定位**（30s）：基于 Spring Boot 3 + MyBatis-Plus + MySQL + Redis 的个人博客后端

2. **核心亮点**（2-3min）：
   - Redis Token 认证 + 会话管理 + 登录限流
   - 文章缓存 + 缓存失效策略
   - 异步浏览量统计
   - 双角色权限模型
   - Flyway + Docker Compose 工程化

3. **主动暴露不足**（1min）：
   - `KEYS` 扫描可优化为 `SCAN`
   - N+1 查询需要批量组装
   - 集成测试待补充
   - 上传缺少文件魔数校验

4. **延伸思考**（1min）：
   - 高并发下的优化方向
   - 安全加固计划
   - 可观测性建设

这样既展示了完成的工作，也体现了对工程质量的认知和持续改进的意识。
