FROM clojure:openjdk-8-lein

COPY target/cmr-service-bridge-*-SNAPSHOT-standalone.jar /app/cmr-service-bridge.jar
COPY resources/ /app/resources/
EXPOSE 3013
WORKDIR /app
CMD "java" "-classpath" "cmr-service-bridge.jar" "clojure.main" "-m" "cmr.opendap.core"
