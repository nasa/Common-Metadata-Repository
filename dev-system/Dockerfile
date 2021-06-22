FROM clojure:openjdk-8-lein

COPY target/cmr-dev-system-0.1.0-SNAPSHOT-standalone.jar /app/
EXPOSE 2999
EXPOSE 3001
EXPOSE 3002
EXPOSE 3003
EXPOSE 3004
EXPOSE 3005
EXPOSE 3006
EXPOSE 3008
EXPOSE 3009
EXPOSE 3010
EXPOSE 3011
EXPOSE 6379
EXPOSE 9210

WORKDIR /app
CMD "java" "-classpath" "/app/cmr-dev-system-0.1.0-SNAPSHOT-standalone.jar" "cmr.dev_system.runner"
