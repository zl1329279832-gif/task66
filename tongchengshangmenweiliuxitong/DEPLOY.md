# 部署文档 — 同城上门喂遛宠物系统

## 1. 项目结构与多入口说明

```
tongchengshangmenweiliuxitong/
├── src/main/java/com/          # Java 后端源码
├── src/main/resources/
│   ├── application.yml         # 公共配置（MyBatis-Plus、Multipart 等）
│   ├── application-local.yml   # 本地开发环境
│   ├── application-staging.yml # 测试/预发布环境
│   ├── application-prod.yml    # 生产环境
│   ├── static/                 # 静态资源（含预置图片）
│   ├── admin/admin/            # Vue 管理端源码
│   │   ├── src/                #   Vue 源码
│   │   ├── dist/               #   构建产物（npm run build 生成）
│   │   └── package.json
│   └── front/front/            # H5 前台页面（纯 HTML + jQuery/Layui）
│       ├── index.html          #   前台入口
│       ├── js/config.js        #   前台菜单 & 轮播配置
│       └── modules/http/       #   HTTP 请求模块
├── Dockerfile                  # 多阶段 Docker 构建
├── docker-compose.yml          # 一键启动（应用 + MySQL）
└── pom.xml                     # Maven 构建（含 local/staging/prod Profile）
```

### 访问路径映射

| 入口 | URL 路径 | 资源来源 |
|------|----------|----------|
| H5 前台 | `{host}/tongchengshangmenweiliuxitong/front/index.html` | `classpath:/front/front/index.html` |
| 管理后台 | `{host}/tongchengshangmenweiliuxitong/admin/dist/index.html` | `classpath:/admin/admin/dist/index.html` |
| API 接口 | `{host}/tongchengshangmenweiliuxitong/**` | Spring MVC Controllers |
| 上传文件 | `{host}/tongchengshangmenweiliuxitong/upload/{file}` | 外挂上传目录 `app.upload-dir` |

> 前端的 API base URL 已改为 `window.location.protocol + '//' + window.location.host + '/tongchengshangmenweiliuxitong/'`，换域名时**无需修改任何 JS 文件**。

---

## 2. Maven Profile 说明

| Profile | 激活方式 | 端口 | 数据库 | 上传目录 | SQL 日志 |
|---------|----------|------|--------|----------|----------|
| `local` | 默认 (`mvn package`) | 8080 | localhost:3306 root/123456 | `./upload` | DEBUG |
| `staging` | `mvn package -P staging` | 8080 | `${DB_HOST}:${DB_PORT}` | `${UPLOAD_DIR}` 默认 `/data/upload` | DEBUG |
| `prod` | `mvn package -P prod` | 8080 | `${DB_HOST}:${DB_PORT}` | `${UPLOAD_DIR}` 默认 `/data/upload` | WARN |

staging/prod 环境通过**环境变量**注入敏感配置：

```bash
DB_HOST=10.0.1.100  DB_PORT=3306  DB_USER=petapp  DB_PASS=secret  UPLOAD_DIR=/data/upload
```

---

## 3. 本地开发

```bash
# 1. 启动 MySQL 并导入 SQL
mysql -u root -p123456 < tongchengshangmenweiliuxitong.sql

# 2. 启动后端（local profile 默认激活）
cd tongchengshangmenweiliuxitong
mvn spring-boot:run

# 3. 启动 Vue Admin 开发服务器（可选，热更新）
cd src/main/resources/admin/admin
npm install
npm run serve
# 管理端开发: http://localhost:8081
# 前台页面:   http://localhost:8080/tongchengshangmenweiliuxitong/front/index.html
```

---

## 4. 构建流程（CI / 手动）

```bash
# Step 1: 构建 Vue Admin
cd tongchengshangmenweiliuxitong/src/main/resources/admin/admin
npm ci
npm run build    # 产物输出到 dist/

# Step 2: 构建 Spring Boot JAR（admin dist 已在 classpath 内）
cd tongchengshangmenweiliuxitong
mvn clean package -P prod -DskipTests

# 产物: target/tongchengshangmenweiliuxitong-0.0.1-SNAPSHOT.jar
```

> **CI 流水线** (`.github/workflows/ci.yml`) 已自动串联以上两步，任一步失败即红。

---

## 5. Docker 部署

### 5.1 使用 docker-compose（推荐）

```bash
cd tongchengshangmenweiliuxitong

# 启动应用 + MySQL
docker-compose up -d

# 查看日志
docker-compose logs -f app
```

默认端口 `8080`，可在 `docker-compose.yml` 的 `ports` 中修改宿主机映射。

### 5.2 单独构建镜像

```bash
cd tongchengshangmenweiliuxitong

# 构建（多阶段，含前端编译）
docker build -t pet-platform:latest .

# 运行
docker run -d \
  --name pet-app \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=your-db-host \
  -e DB_USER=root \
  -e DB_PASS=secret \
  -v pet-upload:/data/upload \
  pet-platform:latest
```

### 5.3 Docker 安全说明

- 容器内以 **非 root 用户** (`appusr:appgrp`) 运行
- 上传目录 `/data/upload` 声明为 `VOLUME`，数据持久化不随容器销毁
- 敏感信息（数据库密码）通过环境变量注入，**不要硬编码到镜像中**

---

## 6. HTTPS 反向代理与 AuthorizationInterceptor Token 配置

### 问题背景

`AuthorizationInterceptor` 从 HTTP Header `Token` 读取令牌：

```java
String token = request.getHeader("Token");
```

### HTTPS 反向代理（Nginx）配置要点

**1. 必须透传自定义 Header `Token`**

Nginx 默认会透传自定义 Header，但如果 Header 名称包含下划线，需要开启 `underscores_in_headers on;`。`Token` 不含下划线，**默认配置即可透传，无需额外配置**。

**2. 推荐的 Nginx 配置**

```nginx
server {
    listen 443 ssl;
    server_name pet.example.com;

    ssl_certificate     /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;

    location /tongchengshangmenweiliuxitong/ {
        proxy_pass http://127.0.0.1:8080/tongchengshangmenweiliuxitong/;

        # 标准反代头 —— 让后端知道真实客户端 IP 和协议
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Token Header 无需额外配置，Nginx 默认透传
        # 如果你用了 proxy_set_header 覆盖了所有 Header，
        # 需确保没有丢掉 Token，可显式加一行：
        # proxy_set_header Token $http_token;
    }
}
```

**3. CORS 说明**

`AuthorizationInterceptor` 已内置 CORS 响应头（`Access-Control-Allow-Headers` 包含 `Token`）。
如果 Nginx 层也配置了 CORS，注意**不要重复设置**，否则浏览器会报 "multiple CORS headers" 错误。
建议只在一层（后端 OR Nginx）统一处理 CORS。

**4. 总结：Token 在 HTTPS 反代下是否需要额外配置？**

| 场景 | 是否需要额外配置 |
|------|-----------------|
| 标准 Nginx 反代 | **不需要**，`Token` Header 默认透传 |
| 自定义了 `proxy_set_header` 且覆盖了默认行为 | 需要显式加 `proxy_set_header Token $http_token;` |
| Nginx 层也配了 CORS | 需要去掉一层的 CORS 配置，避免重复 |
| 使用 CDN/WAF | 检查 CDN 是否 strip 了自定义 Header，必要时加白名单 |

---

## 7. 上传目录说明

| 环境 | 配置项 `app.upload-dir` | 说明 |
|------|------------------------|------|
| local | `./upload` | 项目工作目录下的 `upload/` 文件夹 |
| staging/prod | `/data/upload`（可通过 `UPLOAD_DIR` 环境变量覆盖） | Docker 容器内路径，挂载为 Volume |

上传文件通过 `InterceptorConfig` 注册的资源处理器对外提供访问：
`/upload/**` → `file:{app.upload-dir}/`

---

## 8. 环境切换速查

```bash
# 本地开发
mvn spring-boot:run                          # 默认 local

# 测试环境打包
mvn clean package -P staging -DskipTests

# 生产环境打包
mvn clean package -P prod -DskipTests

# Docker 切换环境
docker run -e SPRING_PROFILES_ACTIVE=staging ...
docker run -e SPRING_PROFILES_ACTIVE=prod ...
```
