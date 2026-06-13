# ============================================================
# 多阶段构建：前端 + 后端 + 运行时
# 构建上下文：tongchengshangmenweiliuxitong/
# ============================================================

# ---- Stage 1: 构建 Vue Admin ----
FROM node:14-alpine AS frontend
WORKDIR /build/admin
COPY src/main/resources/admin/admin/package*.json ./
RUN npm ci --production=false
COPY src/main/resources/admin/admin/ ./
RUN npm run build

# ---- Stage 2: 构建 Spring Boot JAR ----
FROM maven:3.6-jdk-8-alpine AS backend
WORKDIR /build
COPY pom.xml ./
RUN mvn dependency:go-offline -B
COPY src/ ./src/
# 将前端构建产物复制到 admin dist
COPY --from=frontend /build/admin/dist/ ./src/main/resources/admin/admin/dist/
RUN mvn package -P prod -DskipTests -B

# ---- Stage 3: 运行时（非 root） ----
FROM openjdk:8-jre-alpine
LABEL maintainer="pet-platform"

RUN addgroup -S appgrp && adduser -S appusr -G appgrp

WORKDIR /app
COPY --from=backend /build/target/tongchengshangmenweiliuxitong-0.0.1-SNAPSHOT.jar app.jar

# 创建上传目录并设置权限
RUN mkdir -p /data/upload && chown -R appusr:appgrp /app /data/upload

# 声明外挂卷
VOLUME /data/upload

# 切换到非 root 用户
USER appusr

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar", \
  "--spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]
