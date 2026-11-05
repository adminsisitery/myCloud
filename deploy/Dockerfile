FROM eclipse-temurin:21-jre

ARG JAR_FILE
COPY ${JAR_FILE} /app/app.jar

ENTRYPOINT ["java", \
  "-Xms128m", "-Xmx192m", \
  "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=200", \
  "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/opt/mycloud/logs", \
  "-jar", "/app/app.jar"]