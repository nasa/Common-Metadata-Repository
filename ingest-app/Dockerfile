FROM centos:7

RUN yum install -y java-1.8.0-openjdk

COPY . target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar

WORKDIR /app

CMD ["java", "-classpath", "/app/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar", "cmr.ingest_app.runner"]
