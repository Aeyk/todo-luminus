FROM openjdk:8-alpine

COPY target/uberjar/luminus-full-stack.jar /luminus-full-stack/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/luminus-full-stack/app.jar"]
