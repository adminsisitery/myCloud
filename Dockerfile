FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system mycloud && useradd --system --gid mycloud --home-dir /app mycloud && mkdir -p /app/logs && chown -R mycloud:mycloud /app
WORKDIR /app
COPY --chown=mycloud:mycloud target/cloud-drive-backend-1.0-SNAPSHOT.jar app.jar
USER mycloud
ENV TZ=Asia/Shanghai MYCLOUD_LOG_PATH=/app/logs
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]
