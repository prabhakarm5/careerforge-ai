# ============================
# Stage 1 - Build
# ============================
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# ============================
# Stage 2 - Runtime
# ============================
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

ENV SERVER_PORT=5000

# Keep enough RAM available for nginx, Docker and the Elastic Beanstalk health
# agent on the 1 GB t3.micro host. Without an explicit heap cap the JVM can
# expand until AWS stops receiving instance health data.
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx320m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"

EXPOSE 5000

ENTRYPOINT ["java", "-jar", "app.jar"]