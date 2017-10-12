FROM centos:7

# Install Java and clean up.
RUN curl -LO --cookie 'oraclelicense=accept-securebackup-cookie' 'http://download.oracle.com/otn-pub/java/jdk/8u45-b14/jdk-8u45-linux-x64.rpm' \
 && rpm -Uvh jdk-8u45-linux-x64.rpm \
 && rm jdk-8u45-linux-x64.rpm
ENV JAVA_HOME /usr/java/jdk1.8.0_45/jre

COPY target/cmr-index-set-app-0.1.0-SNAPSHOT-standalone.jar /app/
EXPOSE 3005

WORKDIR /app

CMD ["java", "-classpath", "/app/cmr-index-set-app-0.1.0-SNAPSHOT-standalone.jar", "cmr.index-set.runner"]
