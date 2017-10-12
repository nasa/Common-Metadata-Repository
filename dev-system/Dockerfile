FROM centos:7

# Install Java and clean up.
RUN curl -LO --cookie 'oraclelicense=accept-securebackup-cookie' 'http://download.oracle.com/otn-pub/java/jdk/8u45-b14/jdk-8u45-linux-x64.rpm' \
 && rpm -Uvh jdk-8u45-linux-x64.rpm \
 && rm jdk-8u45-linux-x64.rpm
ENV JAVA_HOME /usr/java/jdk1.8.0_45/jre

COPY target/cmr-dev-system-0.1.0-SNAPSHOT-standalone.jar /app/
EXPOSE 2999
EXPOSE 3001
EXPOSE 3002
EXPOSE 3003
EXPOSE 3004
EXPOSE 3005
EXPOSE 3006
EXPOSE 3007
EXPOSE 3008
EXPOSE 3009
EXPOSE 3010
EXPOSE 3011
EXPOSE 9210

WORKDIR /app
CMD "java" "-classpath" "/app/cmr-dev-system-0.1.0-SNAPSHOT-standalone.jar" "cmr.dev_system.runner"
