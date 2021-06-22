FROM clojure:openjdk-8-lein

COPY target/cmr-access-control-app-0.1.0-SNAPSHOT-standalone.jar /app/
EXPOSE 3011

WORKDIR /app

CMD "java" "-classpath" "/app/cmr-access-control-app-0.1.0-SNAPSHOT-standalone.jar" "clojure.main" "-m" "cmr.access-control.runner"
