FROM eclipse-temurin:17

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive \
    apt-get install --assume-yes \
      python3

RUN mkdir /opt/cmr

EXPOSE 2999-3012
EXPOSE 6379
EXPOSE 9010-9012
EXPOSE 9210
EXPOSE 9211

COPY target/cmr-dev-system-0.1.0-SNAPSHOT-standalone.jar /opt/cmr/cmr.jar

WORKDIR /opt/cmr

CMD ["java", "-classpath", "/opt/cmr/cmr.jar", "cmr.dev_system.runner"]
