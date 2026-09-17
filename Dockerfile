# 1단계: 빌드 — Maven 캐시가 최대한 재사용되도록 의존성 다운로드와 소스 복사를 분리한다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# 2단계: 실행 — JDK가 아닌 JRE만 담아 이미지 용량을 줄인다.
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --create-home --shell /bin/false appuser
COPY --from=build /app/target/demo-0.0.1-SNAPSHOT.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
