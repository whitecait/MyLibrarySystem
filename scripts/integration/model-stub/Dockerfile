FROM eclipse-temurin:21.0.8_9-jdk-jammy@sha256:adb9b2d15adf1833d9dae0bdc1cff61ef5a804dc58dfbfb34269f32432b2e5dc AS build
WORKDIR /build
COPY ModelStub.java ./
RUN javac --add-modules jdk.httpserver ModelStub.java

FROM eclipse-temurin:21.0.8_9-jre-jammy@sha256:db1689535962d757a5adabf57387584ed543d38c0b9d1fe870123ea362ad73b0
WORKDIR /app
COPY --from=build /build/ModelStub.class ./
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "--add-modules", "jdk.httpserver", "-cp", "/app", "ModelStub"]
