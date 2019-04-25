FROM clojure

COPY target/cmr-index-set-app-0.1.0-SNAPSHOT-standalone.jar /app/
EXPOSE 3005

WORKDIR /app

CMD "java" "-classpath" "/app/cmr-index-set-app-0.1.0-SNAPSHOT-standalone.jar" "clojure.main" "-m" "cmr.index-set.runner"
