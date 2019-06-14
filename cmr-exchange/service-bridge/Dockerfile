FROM clojure

COPY target/cmr-service-bridge-*-SNAPSHOT-standalone.jar /app/cmr-standalone.jar
COPY resources/ /app/resources/
EXPOSE 3013

WORKDIR /app

CMD "java" "-jar" "cmr-standalone.jar"
