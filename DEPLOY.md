# 部署文档 — 同城上门喂遛宠物系统

## 1. 目录结构总览

```
tongchengshangmenweiliuxitong/
├── pom.xml                              ← Maven 主 POM（含 profiles）
├── src/main/java/com/
│   ├── controller/                      ← 业务接口
│   ├── interceptor/
│   │   └── AuthorizationInterceptor.java  ← Token 鉴权
│   └── config/
│       └── InterceptorConfig.java       ← 静态资源映射
├── src/main/resources/
│   ├── application.yml                  ← 公共配置
│   ├── application-local.yml            ← 本地开发
│   ├── application-staging.yml          ← 测试/预发布
│   ├── application-prod.yml             ← 生产
│   ├── admin/admin/                     ← Vue 2 管理端源码
│   │   ├── package.json
│   │   ├── vue.config.js
│   │   ├── src/                         ← Vue 源码
│   │   └── dist/                        ← npm run build 产物（打入 JAR）
│   ├── front/front/                     ← H5 前台（纯静态 HTML/JS/CSS）
│   │   ├── index.html
│   │   ├── pages/                       ← 各业务页面
│   │   └── js/config.js                 ← 前台全局配置
│   ├── static/upload/                   ← 本地开发上传目录
│   └── mapper/                          ← MyBatis XML
```

## 2. Maven Profile 说明

| Profile   | 激活方式                | 端口   | context-path                    | 上传目录         | 数据库               |
|-----------|------------------------|--------|--------------------------------|-----------------|---------------------|
| `local`   | `-Plocal`（默认）       | 8080   | `/tongchengshangmenweiliuxitong` | `static/upload` | `127.0.0.1:3306`    |
| `staging` | `-Pstaging`            | 8080   | `/tongchengshangmenweiliuxitong` | `/data/upload`  | 环境变量 `DB_HOST`   |
| `prod`    | `-Pprod`               | 8080   | `/tongchengshangmenweiliuxitong` | `/data/upload`  | 环境变量 `DB_HOST`   |

构建命令：

```bash
# 本地开发（IDE 直接运行即可，默认 local）
mvn spring-boot:run

# 测试环境
mvn clean package -Pstaging

# 生产环境
mvn clean package -Pprod
```

环境变量（staging/prod 可用）：

| 变量        | 说明              | 默认值                |
|------------|------------------|----------------------|
| `DB_HOST`  | MySQL 主机        | `staging-db`/`prod-db` |
| `DB_PORT`  | MySQL 端口        | `3306`               |
| `DB_USER`  | 数据库用户         | `tongcheng_staging`  |
| `DB_PASS`  | 数据库密码         | `changeme`           |

## 3. 前端构建与资源路径

### 3.1 管理端（Vue Admin）

- **源码位置**: `src/main/resources/admin/admin/`
- **构建命令**: `npm ci && npm run build`
- **产物位置**: `src/main/resources/admin/admin/dist/`
- **访问路径**: `http(s)://域名/{context-path}/admin/dist/index.html`
- **API 请求 baseURL**: `/tongchengshangmenweiliuxitong`（`src/utils/http.js` 中配置，与 context-path 保持一致）

### 3.2 前台 H5（静态页面）

- **源码位置**: `src/main/resources/front/front/`
- **无需构建**: 纯 HTML/JS/CSS，Layui + Element UI CDN
- **访问路径**: `http(s)://域名/{context-path}/front/index.html`
- **API 请求**: 页面内使用相对路径 + context-path 前缀调用后端接口

### 3.3 静态资源映射（InterceptorConfig.java）

Spring Boot 通过 `addResourceHandlers` 映射以下 classpath 目录：

```
classpath:/resources/  →  /**
classpath:/static/     →  /**
classpath:/admin/      →  /**    （Vue 管理端 dist 在此）
classpath:/img/        →  /**
classpath:/front/      →  /**    （H5 前台在此）
classpath:/public/     →  /**
```

这意味着 Vue 构建产物 `admin/admin/dist/` 在打包后位于 JAR 内的 `classpath:/admin/admin/dist/`，可通过 URL `/{context-path}/admin/dist/index.html` 直接访问。

### 3.4 换域名 / 换 context-path 时需要改的文件

| 文件 | 需改内容 | 说明 |
|------|---------|------|
| `application-{profile}.yml` | `server.servlet.context-path` | 全局 context-path |
| `admin/admin/src/utils/http.js` | `baseURL` | Axios 请求前缀 |
| `admin/admin/src/utils/base.js` | `url`, `indexUrl` | 管理端跳转链接 |
| `admin/admin/vue.config.js` | `devServer.proxy` 目标地址 | 仅开发时代理 |
| `front/front/js/config.js` | `adminurl` 变量 | 前台跳管理端链接（已标注"已废弃"） |
| `front/front/pages/*/detail.html` | `createElement(...)` 中的硬编码 URL | 分享链接（约 10 处） |

> **建议**: 将 `base.js` 和 `config.js` 中的硬编码地址改为读取 `window.location.origin + contextPath`，即可一处改动适配所有环境。详见下文「推荐的前端改造」。

## 4. CI 流水线

### GitHub Actions (`.github/workflows/ci.yml`)

```
push / PR / 手动触发
  ↓
checkout → setup JDK 8 → setup Node 16
  ↓
npm ci && npm run build     ← 在 admin/admin/ 目录下
  ↓
mvn clean package -P{profile}
  ↓
upload JAR artifact
```

- **任一步骤失败即标红**（GitHub Actions 默认 fail-fast）
- 手动触发时可在 Actions 页面选择 Maven profile
- Node 缓存依赖 `package-lock.json`，Maven 缓存走 `actions/setup-java` 内置

## 5. Docker 部署

### 5.1 构建镜像

```bash
# 默认 staging profile
docker build -t pet-service .

# 指定 prod profile
docker build --build-arg MVN_PROFILE=prod -t pet-service .
```

### 5.2 镜像特性

| 特性 | 说明 |
|------|------|
| 基础镜像 | `openjdk:8-jre-slim`（约 200 MB） |
| 运行用户 | `appuser`（非 root，UID 自动分配） |
| 上传目录 | `/data/upload` — 声明为 `VOLUME`，需外挂持久化 |
| JVM 参数 | 通过 `JAVA_OPTS` 环境变量注入，默认 `-Xms256m -Xmx512m` |

### 5.3 运行容器

```bash
docker run -d \
  --name pet-service \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=prod-db \
  -e DB_USER=tongcheng_prod \
  -e DB_PASS=your_secure_password \
  -e JAVA_OPTS="-Xms512m -Xmx1024m" \
  -v /host/upload:/data/upload \
  pet-service
```

### 5.4 docker-compose 一键启动

```bash
docker compose up -d --build
```

会同时启动 MySQL 8.0 + 应用容器，上传文件持久化到 `upload_data` 卷。

## 6. Nginx HTTPS 反向代理配置

### 6.1 推荐配置

```nginx
upstream pet_backend {
    server 127.0.0.1:8080;
}

server {
    listen 443 ssl http2;
    server_name pet.example.com;

    ssl_certificate     /etc/ssl/certs/pet.example.com.pem;
    ssl_certificate_key /etc/ssl/private/pet.example.com.key;

    # 关键：将真实协议和主机透传给后端
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    # Spring Boot 识别这些 header 需要额外配置（见 6.2）
    proxy_set_header X-Forwarded-Port  $server_port;

    location /tongchengshangmenweiliuxitong/ {
        proxy_pass http://pet_backend;
    }
}

server {
    listen 80;
    server_name pet.example.com;
    return 301 https://$host$request_uri;
}
```

### 6.2 Spring Boot 识别 Forwarded Headers

当应用运行在 Nginx 反代后面时，`request.getRemoteAddr()` 返回的是 Nginx 的 IP，`request.getScheme()` 返回 `http`。要让 Spring Boot 正确读取真实客户端信息，需在 `application-prod.yml`（或 `application-staging.yml`）中追加：

```yaml
server:
    use-forward-headers: true    # Spring Boot 2.2+ 支持
    forward-headers-strategy: framework  # 使用 ForwardedHeaderFilter
```

> **注意**: 当前项目 Spring Boot 2.2.2，`use-forward-headers: true` 即可。如果是 Spring Boot 2.6+，使用 `forward-headers-strategy: framework`。

### 6.3 AuthorizationInterceptor 的 Token Header 与 HTTPS 反代

**现状分析**：

`AuthorizationInterceptor` 从 HTTP 请求头 `Token` 字段读取鉴权令牌：

```java
String token = request.getHeader("Token");  // LOGIN_TOKEN_KEY = "Token"
```

**HTTPS 反代下是否需要额外配置？**

| 场景 | 结论 |
|------|------|
| Nginx → HTTP → Spring Boot（最常见） | **无需额外配置**。Nginx 默认透传所有自定义 Header（包括 `Token`），Spring Boot 收到的请求与直连无异。 |
| Nginx 显式清除了某些 Header | 需确保 `proxy_pass_request_headers on;`（默认开启），且没有 `proxy_set_header Token "";` 这类覆盖。 |
| 经过 WAF/CDN（如 Cloudflare） | 部分 CDN 会剥离非标准 Header。若 `Token` 被剥离，建议：① 改用标准 `Authorization: Bearer xxx` 方案；② 或在 CDN 规则中白名单放行 `Token` header。 |
| HTTPS 终结在 Nginx，后端走 HTTP | **不影响 Header**。HTTPS 加解密仅在 Nginx ↔ 浏览器之间，Header 内容不受影响。 |

**结论：标准 Nginx 反代场景下，`Token` Header 不需要额外配置**。只需确保：

1. Nginx 配置中没有显式覆盖或删除 `Token` header
2. 如果使用 CDN/WAF，确认 `Token` 自定义 header 未被过滤
3. 前端的 `http.js` 已在请求拦截器中设置 `config.headers['Token'] = storage.get('Token')`

**推荐改进**（不改业务代码，仅供参考）：

当前 `Access-Control-Allow-Origin` 直接反射请求 `Origin`，在 HTTPS 下存在安全风险。生产环境建议改为白名单域名：

```java
// AuthorizationInterceptor.java 第 48 行
// 当前：response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
// 建议改为：
String origin = request.getHeader("Origin");
if (ALLOWED_ORIGINS.contains(origin)) {
    response.setHeader("Access-Control-Allow-Origin", origin);
}
```

## 7. 上传文件存储

### 7.1 当前行为

`FileController.upload()` 将文件写入 `classpath:static/upload/` 目录。在 JAR 包部署模式下，这意味着文件写入到 **JAR 运行目录下的 `static/upload/`**。

### 7.2 Docker 部署的上传卷

Docker 镜像将 `/data/upload` 声明为 VOLUME。要让上传文件持久化，运行容器时必须挂载宿主机目录：

```bash
-v /path/on/host/upload:/data/upload
```

### 7.3 生产环境建议

当前 `FileController` 硬编码写入 `classpath:static`，在 JAR 包内运行时写入的是临时解压目录。**生产环境建议**：

1. 将上传目录改为外部路径（如 `/data/upload`），通过 `app.upload.dir` 配置项读取
2. 配置 Nginx 直接代理 `/upload/` 路径到宿主机目录，避免文件流经 Java 层

```nginx
location /tongchengshangmenweiliuxitong/upload/ {
    alias /data/upload/;
    expires 30d;
    add_header Cache-Control "public, immutable";
}
```

## 8. 推荐的前端改造（消除硬编码地址）

为避免换域名时逐个修改 JS 文件，建议在 `base.js` 中使用动态地址：

```js
// admin/admin/src/utils/base.js — 改造后
const base = {
    get() {
        const ctx = '/tongchengshangmenweiliuxitong';  // 与 context-path 保持一致
        return {
            url: window.location.origin + ctx + '/',
            name: 'tongchengshangmenweiliuxitong',
            indexUrl: window.location.origin + ctx + '/front/index.html'
        };
    },
    getProjectName() {
        return { projectName: '同城上门喂遛宠物系统' };
    }
};
export default base;
```

这样只需改 `application-{profile}.yml` 中的 `context-path` 和 `base.js` 中的 `ctx` 常量，即可适配所有环境。

---

*文档最后更新: 2026-06-13*
