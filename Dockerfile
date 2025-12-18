FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# 1. ARG를 먼저 선언
ARG GH_PACKAGES_USER
ARG GH_PACKAGES_TOKEN
ENV GH_PACKAGES_USER=$GH_PACKAGES_USER
ENV GH_PACKAGES_TOKEN=$GH_PACKAGES_TOKEN

# 2. 그 다음 파일 복사
COPY build.gradle settings.gradle gradlew /app/
COPY gradle /app/gradle

# 3. 이제 인증정보가 있으니 의존성 다운로드 가능
RUN ./gradlew dependencies --no-daemon || true

COPY . /app

RUN ./gradlew clean bootJar --no-daemon