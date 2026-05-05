# 个人博客网站系统 API 设计

## 1. API 设计约定

本文档描述个人博客网站系统的 REST API。接口面向未确定前端技术栈设计，后端默认采用 Spring Boot。

### 1.1 基础路径

建议统一前缀：

```text
/api
```

示例：

```text
GET /api/articles
POST /api/admin/articles
```

### 1.2 权限标记

| 标记 | 说明 |
| --- | --- |
| `PUBLIC` | 无需登录 |
| `GUEST` | 游客或管理员登录后可访问 |
| `ADMIN` | 仅管理员可访问 |

### 1.3 认证方式

登录成功后返回 `token`。需要登录的接口通过 Header 传递：

```http
Authorization: Bearer <token>
```

Redis 中使用 `auth:token:{token}` 保存登录态。

### 1.4 命名约定

- API JSON 字段统一使用 `camelCase`，例如 `coverImageUrl`。
- MySQL 字段统一使用 `snake_case`，例如 `cover_image_url`。
- 密码字段只允许出现在请求体中，任何响应都不返回密码明文或密码哈希。

### 1.5 统一响应结构

成功响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {}
}
```

失败响应：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "title is required",
  "data": null
}
```

### 1.6 分页响应结构

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "items": [],
    "page": 1,
    "size": 10,
    "total": 100,
    "pages": 10
  }
}
```

分页参数约定：

| 参数 | 说明 | 默认值 |
| --- | --- | --- |
| `page` | 页码，从 1 开始 | `1` |
| `size` | 每页数量 | `10` |

### 1.7 通用错误码

| 错误码 | 说明 |
| --- | --- |
| `SUCCESS` | 成功 |
| `VALIDATION_ERROR` | 请求参数错误 |
| `UNAUTHORIZED` | 未登录或 token 无效 |
| `FORBIDDEN` | 无权限 |
| `NOT_FOUND` | 资源不存在 |
| `CONFLICT` | 资源冲突，例如用户名或 slug 重复 |
| `RATE_LIMITED` | 请求过于频繁 |
| `UPLOAD_ERROR` | 上传失败 |
| `INTERNAL_ERROR` | 服务端错误 |

## 2. 认证接口

### 2.1 游客注册

```http
POST /api/auth/guest/register
```

权限：`PUBLIC`

请求：

```json
{
  "username": "visitor01",
  "password": "password123",
  "nickname": "Visitor"
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "id": 1001,
    "username": "visitor01",
    "nickname": "Visitor",
    "role": "GUEST"
  }
}
```

校验规则：

- `username` 必填，长度 3-64，只允许字母、数字、下划线、短横线。
- `password` 必填，长度 6-64。
- `nickname` 可选，长度 1-64；不传时默认等于 `username`。
- `username` 不可重复。
- 注册成功后不会自动登录，前端继续调用登录接口获取 token。

### 2.2 登录

```http
POST /api/auth/login
```

权限：`PUBLIC`

管理员和游客共用此接口，通过账号角色区分权限。

请求：

```json
{
  "username": "admin",
  "password": "password123"
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "token": "eyJhbGciOi...",
    "expiresIn": 604800,
    "user": {
      "id": 1,
      "username": "admin",
      "nickname": "Admin",
      "role": "ADMIN"
    }
  }
}
```

校验规则：

- 用户不存在、密码错误、用户被禁用时返回 `UNAUTHORIZED`。
- 登录失败触发 Redis 限流计数。
- 响应不返回密码哈希。

### 2.3 退出登录

```http
POST /api/auth/logout
```

权限：`GUEST`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": true
}
```

处理规则：

- 删除 Redis 中当前 token。

### 2.4 当前登录用户

```http
GET /api/auth/me
```

权限：`GUEST`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "id": 1001,
    "username": "visitor01",
    "nickname": "Visitor",
    "role": "GUEST"
  }
}
```

## 3. 前台公开接口

### 3.1 获取封面配置

```http
GET /api/cover
```

权限：`PUBLIC`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "title": "Sam's Blog",
    "subtitle": "Writing, coding and life",
    "backgroundImageUrl": "/uploads/cover/cover.jpg",
    "avatarImageUrl": "/uploads/profile/avatar.jpg",
    "links": [
      {
        "label": "Articles",
        "url": "/articles",
        "type": "internal",
        "sortOrder": 1
      }
    ]
  }
}
```

### 3.2 获取信息页

```http
GET /api/profile
```

权限：`PUBLIC`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "displayName": "Sam Lee",
    "bio": "Personal blog owner",
    "avatarImageUrl": "/uploads/profile/avatar.jpg",
    "email": "me@example.com",
    "location": "Shanghai",
    "socialLinks": [],
    "contentMarkdown": "## About me"
  }
}
```

### 3.3 获取文章列表

```http
GET /api/articles?page=1&size=10&categorySlug=tech&tagSlug=spring
```

权限：`PUBLIC`

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `page` | 否 | 页码 |
| `size` | 否 | 每页数量 |
| `categorySlug` | 否 | 分类筛选 |
| `tagSlug` | 否 | 标签筛选 |
| `keyword` | 否 | 标题或摘要关键词 |

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "items": [
      {
        "id": 1,
        "title": "First Article",
        "slug": "first-article",
        "summary": "Article summary",
        "coverImageUrl": "/uploads/article/cover.jpg",
        "category": {
          "id": 1,
          "name": "Tech",
          "slug": "tech"
        },
        "tags": [
          {
            "id": 1,
            "name": "Spring",
            "slug": "spring"
          }
        ],
        "viewCount": 10,
        "publishedAt": "2026-05-04T10:00:00"
      }
    ],
    "page": 1,
    "size": 10,
    "total": 1,
    "pages": 1
  }
}
```

规则：

- 只返回 `PUBLISHED` 且未删除的文章。
- 默认按 `publishedAt` 倒序。

### 3.4 获取文章详情

```http
GET /api/articles/{slug}
```

权限：`PUBLIC`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "id": 1,
    "title": "First Article",
    "slug": "first-article",
    "summary": "Article summary",
    "coverImageUrl": "/uploads/article/cover.jpg",
    "contentMarkdown": "# First Article",
    "contentHtml": null,
    "category": {
      "id": 1,
      "name": "Tech",
      "slug": "tech"
    },
    "tags": [],
    "viewCount": 11,
    "publishedAt": "2026-05-04T10:00:00"
  }
}
```

规则：

- 只允许访问 `PUBLISHED` 且未删除的文章。
- 访问详情时可异步增加浏览量。
- `contentHtml` 第一版可返回 `null`；如果后端负责 Markdown 渲染，则返回清洗后的 HTML。

### 3.5 获取分类列表

```http
GET /api/categories
```

权限：`PUBLIC`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "Tech",
      "slug": "tech",
      "description": "Technical notes"
    }
  ]
}
```

### 3.6 获取标签列表

```http
GET /api/tags
```

权限：`PUBLIC`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "Spring",
      "slug": "spring"
    }
  ]
}
```

## 4. 评论接口

### 4.1 获取文章评论

```http
GET /api/articles/{slug}/comments?page=1&size=20
```

权限：`PUBLIC`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "items": [
      {
        "id": 1,
        "content": "Nice article!",
        "author": {
          "id": 1001,
          "username": "visitor01",
          "nickname": "Visitor"
        },
        "createdAt": "2026-05-04T10:20:00"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1,
    "pages": 1
  }
}
```

规则：

- 只返回 `VISIBLE` 且未删除的评论。
- 响应不返回任何密码相关字段。

### 4.2 发表评论

```http
POST /api/articles/{slug}/comments
```

权限：`GUEST`

请求：

```json
{
  "content": "Nice article!"
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "id": 1,
    "content": "Nice article!",
    "status": "VISIBLE",
    "author": {
      "id": 1001,
      "username": "visitor01",
      "nickname": "Visitor"
    },
    "createdAt": "2026-05-04T10:20:00"
  }
}
```

校验规则：

- 文章必须存在且已发布。
- `content` 必填，长度 1-2000。
- 第一版评论默认状态为 `VISIBLE`。
- 记录 `ipAddress` 和 `userAgent` 供后台审计。

## 5. 管理后台：文章接口

### 5.1 后台获取文章列表

```http
GET /api/admin/articles?page=1&size=10&status=DRAFT&keyword=first
```

权限：`ADMIN`

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `page` | 否 | 页码 |
| `size` | 否 | 每页数量 |
| `status` | 否 | `DRAFT` / `PUBLISHED` / `HIDDEN` |
| `keyword` | 否 | 标题或摘要关键词 |
| `categoryId` | 否 | 分类 ID |

响应字段包含文章的完整管理信息，包括 `status`、`createdAt`、`updatedAt`。

### 5.2 创建文章

```http
POST /api/admin/articles
```

权限：`ADMIN`

请求：

```json
{
  "title": "First Article",
  "slug": "first-article",
  "summary": "Article summary",
  "coverImageUrl": "/uploads/article/cover.jpg",
  "contentMarkdown": "# First Article",
  "categoryId": 1,
  "tagIds": [1, 2],
  "status": "DRAFT"
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "id": 1,
    "title": "First Article",
    "slug": "first-article",
    "status": "DRAFT",
    "createdAt": "2026-05-04T10:00:00"
  }
}
```

校验规则：

- `title` 必填，长度 1-200。
- `contentMarkdown` 必填。
- `slug` 可选；为空时后端根据标题生成。
- `slug` 必须唯一。
- `status` 为空时默认为 `DRAFT`。
- 当 `status = PUBLISHED` 时设置 `publishedAt`。

### 5.3 获取后台文章详情

```http
GET /api/admin/articles/{id}
```

权限：`ADMIN`

规则：

- 可获取草稿、隐藏、已发布文章。
- 返回 `contentMarkdown`、分类、标签和完整管理字段。

### 5.4 更新文章

```http
PUT /api/admin/articles/{id}
```

权限：`ADMIN`

请求：

```json
{
  "title": "Updated Article",
  "slug": "updated-article",
  "summary": "Updated summary",
  "coverImageUrl": "/uploads/article/new-cover.jpg",
  "contentMarkdown": "# Updated Article",
  "categoryId": 1,
  "tagIds": [1, 3],
  "status": "PUBLISHED"
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "id": 1,
    "title": "Updated Article",
    "slug": "updated-article",
    "status": "PUBLISHED",
    "updatedAt": "2026-05-04T11:00:00"
  }
}
```

规则：

- 更新文章和标签关系需要在同一事务内完成。
- 如果文章从非发布状态变为 `PUBLISHED`，设置 `publishedAt`。
- 更新成功后删除文章详情和列表缓存。

### 5.5 删除文章

```http
DELETE /api/admin/articles/{id}
```

权限：`ADMIN`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": true
}
```

规则：

- 使用软删除，设置 `deletedAt`。
- 删除后前台不可见。
- 删除成功后清理文章缓存。

## 6. 管理后台：分类和标签接口

### 6.1 创建分类

```http
POST /api/admin/categories
```

权限：`ADMIN`

请求：

```json
{
  "name": "Tech",
  "slug": "tech",
  "description": "Technical notes",
  "sortOrder": 1
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "id": 1,
    "name": "Tech",
    "slug": "tech",
    "description": "Technical notes",
    "sortOrder": 1
  }
}
```

校验规则：

- `name` 必填，长度 1-64。
- `slug` 可选；为空时后端根据 `name` 生成。
- `slug` 必须唯一。

### 6.2 更新分类

```http
PUT /api/admin/categories/{id}
```

权限：`ADMIN`

请求：

```json
{
  "name": "Tech Notes",
  "slug": "tech-notes",
  "description": "Technical notes",
  "sortOrder": 1
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": true
}
```

规则：

- 分类不存在时返回 `NOT_FOUND`。
- `slug` 和其他分类冲突时返回 `CONFLICT`。

### 6.3 删除分类

```http
DELETE /api/admin/categories/{id}
```

权限：`ADMIN`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": true
}
```

规则：

- 使用软删除。
- 如果分类下存在文章，建议拒绝删除并返回 `CONFLICT`。

### 6.4 创建标签

```http
POST /api/admin/tags
```

权限：`ADMIN`

请求：

```json
{
  "name": "Spring",
  "slug": "spring"
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "id": 1,
    "name": "Spring",
    "slug": "spring"
  }
}
```

校验规则：

- `name` 必填，长度 1-64。
- `slug` 可选；为空时后端根据 `name` 生成。
- `name` 和 `slug` 必须唯一。

### 6.5 更新标签

```http
PUT /api/admin/tags/{id}
```

权限：`ADMIN`

请求：

```json
{
  "name": "Spring Boot",
  "slug": "spring-boot"
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": true
}
```

规则：

- 标签不存在时返回 `NOT_FOUND`。
- `name` 或 `slug` 和其他标签冲突时返回 `CONFLICT`。

### 6.6 删除标签

```http
DELETE /api/admin/tags/{id}
```

权限：`ADMIN`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": true
}
```

规则：

- 使用软删除。
- 删除标签时同步删除 `article_tag` 关系。

## 7. 管理后台：评论接口

### 7.1 后台获取评论列表

```http
GET /api/admin/comments?page=1&size=20&status=VISIBLE&username=visitor01
```

权限：`ADMIN`

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `page` | 否 | 页码 |
| `size` | 否 | 每页数量 |
| `status` | 否 | 评论状态 |
| `articleId` | 否 | 文章 ID |
| `username` | 否 | 游客用户名 |

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "items": [
      {
        "id": 1,
        "articleId": 1,
        "articleTitle": "First Article",
        "content": "Nice article!",
        "status": "VISIBLE",
        "author": {
          "id": 1001,
          "username": "visitor01",
          "nickname": "Visitor"
        },
        "ipAddress": "127.0.0.1",
        "userAgent": "Mozilla/5.0",
        "createdAt": "2026-05-04T10:20:00"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1,
    "pages": 1
  }
}
```

### 7.2 更新评论状态

```http
PATCH /api/admin/comments/{id}/status
```

权限：`ADMIN`

请求：

```json
{
  "status": "HIDDEN"
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": true
}
```

### 7.3 删除评论

```http
DELETE /api/admin/comments/{id}
```

权限：`ADMIN`

规则：

- 使用软删除。

## 8. 管理后台：游客用户接口

### 8.1 获取游客列表

```http
GET /api/admin/guests?page=1&size=20&username=visitor
```

权限：`ADMIN`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "items": [
      {
        "id": 1001,
        "username": "visitor01",
        "nickname": "Visitor",
        "status": "ACTIVE",
        "lastLoginAt": "2026-05-04T10:00:00",
        "createdAt": "2026-05-04T09:00:00"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1,
    "pages": 1
  }
}
```

规则：

- 只返回 `role = GUEST` 的用户。
- 不返回密码哈希。

### 8.2 重置游客密码

```http
PATCH /api/admin/guests/{id}/password
```

权限：`ADMIN`

请求：

```json
{
  "newPassword": "newPassword123"
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": true
}
```

规则：

- `newPassword` 必填，长度 6-64。
- 仅允许重置 `GUEST` 用户密码。
- 使用哈希保存新密码。
- 不支持查看原始密码。

### 8.3 更新游客状态

```http
PATCH /api/admin/guests/{id}/status
```

权限：`ADMIN`

请求：

```json
{
  "status": "DISABLED"
}
```

规则：

- 禁用后该游客不能登录，也不能发表评论。

## 9. 管理后台：管理员账号接口

### 9.1 修改当前管理员密码

```http
PATCH /api/admin/me/password
```

权限：`ADMIN`

请求：

```json
{
  "oldPassword": "oldPassword123",
  "newPassword": "newPassword123"
}
```

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": true
}
```

规则：

- `oldPassword` 必填，需要和当前管理员密码匹配。
- `newPassword` 必填，长度 6-64。
- 只允许管理员修改自己的密码，不提供公开管理员注册接口。
- 修改成功后建议删除该管理员已有登录 token，要求重新登录。

## 10. 管理后台：站点配置接口

### 10.1 获取封面配置

```http
GET /api/admin/cover
```

权限：`ADMIN`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "title": "Sam's Blog",
    "subtitle": "Writing, coding and life",
    "backgroundImageUrl": "/uploads/cover/cover.jpg",
    "avatarImageUrl": "/uploads/profile/avatar.jpg",
    "links": []
  }
}
```

### 10.2 更新封面配置

```http
PUT /api/admin/cover
```

权限：`ADMIN`

请求：

```json
{
  "title": "Sam's Blog",
  "subtitle": "Writing, coding and life",
  "backgroundImageUrl": "/uploads/cover/cover.jpg",
  "avatarImageUrl": "/uploads/profile/avatar.jpg",
  "links": [
    {
      "label": "Articles",
      "url": "/articles",
      "type": "internal",
      "sortOrder": 1
    }
  ]
}
```

规则：

- `title` 必填。
- `links` 可为空数组。
- 更新成功后删除 `site:cover` 缓存。

### 10.3 获取信息页配置

```http
GET /api/admin/profile
```

权限：`ADMIN`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "displayName": "Sam Lee",
    "bio": "Personal blog owner",
    "avatarImageUrl": "/uploads/profile/avatar.jpg",
    "email": "me@example.com",
    "location": "Shanghai",
    "socialLinks": [],
    "contentMarkdown": "## About me"
  }
}
```

### 10.4 更新信息页配置

```http
PUT /api/admin/profile
```

权限：`ADMIN`

请求：

```json
{
  "displayName": "Sam Lee",
  "bio": "Personal blog owner",
  "avatarImageUrl": "/uploads/profile/avatar.jpg",
  "email": "me@example.com",
  "location": "Shanghai",
  "socialLinks": [],
  "contentMarkdown": "## About me"
}
```

规则：

- `displayName` 必填。
- 更新成功后删除 `site:profile` 缓存。

## 11. 图片上传接口

### 11.1 上传图片

```http
POST /api/admin/media/images
Content-Type: multipart/form-data
```

权限：`ADMIN`

表单参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `file` | 是 | 图片文件 |
| `usageType` | 是 | `ARTICLE` / `COVER` / `PROFILE` / `OTHER` |

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "id": 1,
    "originalFilename": "cover.png",
    "contentType": "image/png",
    "fileSize": 102400,
    "usageType": "COVER",
    "url": "/uploads/cover/2026/05/04/uuid.png",
    "createdAt": "2026-05-04T10:00:00"
  }
}
```

校验规则：

- 只允许图片 MIME 类型，例如 `image/jpeg`、`image/png`、`image/webp`、`image/gif`。
- 文件大小根据 `upload.maxFileSizeMb` 限制。
- 服务端生成唯一文件名，避免覆盖。
- 第一版存储到 Docker 挂载目录，例如容器内 `/app/uploads`，静态访问前缀为 `/uploads/**`。
- 上传成功后写入 `media_asset` 表。
- 返回的 `url` 可直接插入 Markdown 或配置到封面、信息页。

### 11.2 后台获取图片列表

```http
GET /api/admin/media/images?page=1&size=20&usageType=ARTICLE
```

权限：`ADMIN`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "items": [
      {
        "id": 1,
        "originalFilename": "cover.png",
        "contentType": "image/png",
        "fileSize": 102400,
        "usageType": "COVER",
        "url": "/uploads/cover/2026/05/04/uuid.png",
        "createdAt": "2026-05-04T10:00:00"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1,
    "pages": 1
  }
}
```

规则：

- `usageType` 可选；不传时查询全部图片。
- 只返回未软删除的图片记录。

### 11.3 删除图片

```http
DELETE /api/admin/media/images/{id}
```

权限：`ADMIN`

规则：

- 使用软删除。
- 可选择异步删除物理文件。
- 已经被文章 Markdown 引用的图片不会自动从 Markdown 中移除。

## 12. 管理后台：系统配置接口

### 12.1 获取系统配置列表

```http
GET /api/admin/system-configs
```

权限：`ADMIN`

响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": [
    {
      "configKey": "upload.maxFileSizeMb",
      "configValue": "10",
      "valueType": "NUMBER",
      "description": "Maximum upload file size in MB",
      "updatedAt": "2026-05-04T10:00:00"
    }
  ]
}
```

### 12.2 更新系统配置

```http
PUT /api/admin/system-configs/{configKey}
```

权限：`ADMIN`

请求：

```json
{
  "configValue": "10",
  "valueType": "NUMBER",
  "description": "Maximum upload file size in MB"
}
```

规则：

- `configKey` 不允许随意修改。
- `valueType` 只能是 `STRING`、`NUMBER`、`BOOLEAN`、`JSON`。
- 更新成功后删除 `site:config` 缓存。

## 13. 关键业务流程

### 13.1 游客评论流程

1. 游客调用 `POST /api/auth/guest/register` 注册账号，必填用户名和密码，昵称可选。
2. 游客调用 `POST /api/auth/login` 登录并获得 token。
3. 游客调用 `POST /api/articles/{slug}/comments` 发表评论。
4. 前台调用 `GET /api/articles/{slug}/comments` 展示评论，只展示用户名或昵称。
5. 管理员可在后台查看评论、隐藏评论、删除评论或重置游客密码。

### 13.2 管理员发布文章流程

1. 管理员登录获得 token。
2. 如需图片，调用 `POST /api/admin/media/images` 上传图片，获得 URL。
3. 管理员在 Markdown 中插入图片 URL。
4. 调用 `POST /api/admin/articles` 创建草稿或直接发布。
5. 文章发布后前台文章列表和详情可见。

### 13.3 修改封面流程

1. 管理员上传封面图片，获得 URL。
2. 调用 `PUT /api/admin/cover` 更新背景图、标题和链接。
3. 后端删除 `site:cover` 缓存。
4. 前台调用 `GET /api/cover` 获取最新封面配置。

## 14. 接口安全要求

- 所有 `ADMIN` 接口必须校验 token 和用户角色。
- 所有密码字段仅允许出现在请求体中，不允许出现在响应体中。
- 登录失败需要限流。
- 上传接口必须校验 MIME 类型、文件大小和扩展名。
- 评论内容需要做长度校验和基础 XSS 防护。
- Markdown 渲染为 HTML 时需要做 HTML 清洗，避免脚本注入。
