FROM centos:7

http://www.npr.org/2017/10/12/556944857/first-listen-bully-losing

COPY . target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar

WORKDIR /app

CMD ["java", "-classpath", "/app/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar", "cmr.ingest.runner"]
