FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY target/device-sender-*-jar-with-dependencies.jar app.jar
ENV DEVICES=5
ENV INGRESS_URL=http://ingress:8082/ingress/readings
ENTRYPOINT ["java","-jar","/app/app.jar"]