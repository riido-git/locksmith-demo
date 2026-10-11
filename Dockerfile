# The demo app, for the three-instance load stack of compose.load.yaml.
# Build the jar first: ./mvnw -DskipTests package
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/locksmith-demo-0.0.1-SNAPSHOT.jar app.jar
# Unpacked, so the jar's libraries are plain files: the h2 service of compose.load.yaml runs H2 from them.
RUN java -Djarmode=tools -jar app.jar extract --destination unpacked && rm app.jar
ENTRYPOINT ["java", "-jar", "unpacked/app.jar"]
