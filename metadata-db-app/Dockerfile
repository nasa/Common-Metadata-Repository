FROM clojure:openjdk-8-lein

COPY target/cmr-metadata-db-app-0.1.0-SNAPSHOT-standalone.jar /app/
EXPOSE 3001

WORKDIR /app

CMD "java" "-classpath" "/app/cmr-metadata-db-app-0.1.0-SNAPSHOT-standalone.jar" "clojure.main" "-m" "cmr.metadata-db.runner"
