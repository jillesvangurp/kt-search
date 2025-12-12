FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew :petstore-demo:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/petstore-demo/build/libs/petstore-demo-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
