FROM cmr-base

COPY target/cmr-cubby-app-0.1.0-SNAPSHOT-standalone.jar /app/
EXPOSE 3007

WORKDIR /app

CMD "java" "-classpath" "/app/cmr-cubby-app-0.1.0-SNAPSHOT-standalone.jar" "cmr.cubby.runner"
