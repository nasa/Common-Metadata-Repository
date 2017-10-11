FROM centos:7

RUN yum install -y java-1.8.0-openjdk

COPY target/cmr-access-control-app-0.1.0-SNAPSHOT-standalone.jar /app/

WORKDIR /app

CMD "java" "-classpath" "/app/cmr-access-control-app-0.1.0-SNAPSHOT-standalone.jar" "cmr.access-control.runner"
