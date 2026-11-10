FROM node:20-alpine AS front
WORKDIR /front
COPY front/package.json front/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY front/ ./
RUN npx ng build

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
COPY --from=front /front/dist/front/browser ./src/main/resources/static
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --home /app explorer
COPY --from=build /build/target/elastic-explorer.jar app.jar
USER explorer
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
