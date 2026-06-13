# ============================================================
#  多阶段 Dockerfile — 同城上门喂遛宠物系统
#  · 非 root 用户运行
#  · 外挂 /data/upload 卷存放上传文件
# ============================================================

# ── 阶段 1：构建 ────────────────────────────────
FROM maven:3.8-openjdk-8 AS builder

# Node 用于构建 Vue Admin
RUN curl -fsSL https://deb.nodesource.com/setup_16.x | bash - && \
    apt-get install -y nodejs

WORKDIR /build

# 先拷贝前端，利用 Docker layer cache
COPY tongchengshangmenweiliuxitong/src/main/resources/admin/admin/package*.json \
     tongchengshangmenweiliuxitong/src/main/resources/admin/admin/
RUN cd tongchengshangmenweiliuxitong/src/main/resources/admin/admin && npm ci

# 拷贝全部源码
COPY . .

# 前端构建 + 后端打包
RUN cd tongchengshangmenweiliuxitong/src/main/resources/admin/admin && npm run build && \
    cd /build/tongchengshangmenweiliuxitong && \
    mvn -B clean package -P${MVN_PROFILE:-staging} -DskipTests

# ── 阶段 2：运行 ────────────────────────────────
FROM openjdk:8-jre-slim

# 创建非 root 用户
RUN groupadd -r appuser && useradd -r -g appuser -m -s /bin/false appuser

# 上传文件挂载点
RUN mkdir -p /data/upload && chown appuser:appuser /data/upload
VOLUME ["/data/upload"]

WORKDIR /app

# 从构建阶段拷贝 JAR
COPY --from=builder --chown=appuser:appuser \
     /build/tongchengshangmenweiliuxitong/target/*.jar /app/app.jar

# 切换到非 root 用户
USER appuser

EXPOSE 8080

# JVM 调优参数可通过 JAVA_OPTS 环境变量注入
ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]
