FROM clojure:openjdk-8-lein

COPY target/cmr-mock-echo-app-0.1.0-SNAPSHOT-standalone.jar /app/
EXPOSE 3008

WORKDIR /app

CMD "java" "-classpath" "/app/cmr-mock-echo-app-0.1.0-SNAPSHOT-standalone.jar" "clojure.main" "-m" "cmr.mock-echo.runner"
