FROM clojure

COPY target/cmr-cubby-app-0.1.0-SNAPSHOT-standalone.jar /app/
EXPOSE 3007

WORKDIR /app

CMD "java" "-classpath" "/app/cmr-cubby-app-0.1.0-SNAPSHOT-standalone.jar" "clojure.main" "-m" "cmr.cubby.runner"
