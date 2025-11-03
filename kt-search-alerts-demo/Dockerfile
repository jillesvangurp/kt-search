FROM amazoncorretto:25
WORKDIR /app

ENV ELASTIC_HOST=localhost
ENV ELASTIC_PORT=9999
ENV ALERT_TARGET=formation-objects
ENV ENVIRONMENT=prod
ENV SLACK_HOOK=""
ENV SENDGRID=""
ENV DEBUG=true

COPY build/libs/kt-search-alerts-demo-all.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
