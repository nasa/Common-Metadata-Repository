FROM clojure:openjdk-8-lein

COPY target/cmr-search-app-0.1.0-SNAPSHOT-standalone.jar /app/
EXPOSE 3003

WORKDIR /app

CMD "java" "-classpath" "/app/cmr-search-app-0.1.0-SNAPSHOT-standalone.jar" "clojure.main" "-m" "cmr.search.runner"
