FROM cmr-base

COPY target/cmr-search-app-0.1.0-SNAPSHOT-standalone.jar /app/
EXPOSE 3003

WORKDIR /app

CMD "java" "-classpath" "/app/cmr-search-app-0.1.0-SNAPSHOT-standalone.jar" "cmr.search.runner"
