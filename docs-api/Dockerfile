# ---- build stage: JDK + Maven, thrown away after the jar is built ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# copy the pom first: dependencies are cached as a layer until the pom changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- runtime stage: only a JRE and the jar ----
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY --from=build /build/target/docs-api-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
