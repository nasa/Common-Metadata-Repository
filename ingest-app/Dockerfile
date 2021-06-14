FROM clojure:openjdk-8-lein

COPY target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar /app/
EXPOSE 3002

WORKDIR /app

CMD "java" "-classpath" "/app/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar" "clojure.main" "-m" "cmr.ingest.runner"
