FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package \
    && find target -name '*.jar' ! -name '*original*' -exec cp {} /src/app.jar \;

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --create-home gitgpt
COPY --from=build /src/app.jar /app/app.jar
USER gitgpt
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
HEALTHCHECK --interval=20s --timeout=5s --start-period=90s --retries=8 \
    CMD curl -fsS http://127.0.0.1:8080/api/health >/dev/null
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
