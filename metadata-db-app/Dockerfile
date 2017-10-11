FROM centos:7

RUN yum install -y java-1.8.0-openjdk

COPY . target/cmr-metadata-db-app-0.1.0-SNAPSHOT-standalone.jar

WORKDIR /app

CMD ["java", "-classpath", "/app/cmr-metadata-db-app-0.1.0-SNAPSHOT-standalone.jar", "cmr.metadata-db_app.runner"]
