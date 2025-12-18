FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

ARG GH_PACKAGES_USER
ARG GH_PACKAGES_TOKEN
ENV GH_PACKAGES_USER=$GH_PACKAGES_USER
ENV GH_PACKAGES_TOKEN=$GH_PACKAGES_TOKEN

COPY build.gradle settings.gradle gradlew /app/
COPY gradle /app/gradle

RUN ./gradlew dependencies --no-daemon || true

COPY . /app

RUN ./gradlew clean bootJar --no-daemon

CMD ["java", "-jar", "/app/build/libs/ticketmon-go-0.0.1-SNAPSHOT.jar"]