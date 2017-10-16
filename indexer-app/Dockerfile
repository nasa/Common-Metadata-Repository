FROM cmr-base

COPY target/cmr-indexer-app-0.1.0-SNAPSHOT-standalone.jar /app/
EXPOSE 3004

WORKDIR /app

CMD "java" "-classpath" "/app/cmr-indexer-app-0.1.0-SNAPSHOT-standalone.jar" "cmr.indexer.runner"
