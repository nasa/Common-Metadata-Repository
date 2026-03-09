ARG BASE_IMAGE_REPO=

FROM ${BASE_IMAGE_REPO}docker.elastic.co/elasticsearch/elasticsearch:8.18.7

USER root
RUN apt update && apt upgrade -y && apt clean

COPY target/cmr-es-spatial-plugin-0.1.0-SNAPSHOT.zip /tmp
RUN mkdir -p /var/lib/elasticsearch/tmp &&\
    chown elasticsearch:elasticsearch /var/lib/elasticsearch/tmp &&\
    chown elasticsearch:elasticsearch /usr/share/elasticsearch &&\
    bin/elasticsearch-plugin install discovery-ec2 --batch &&\
    bin/elasticsearch-plugin install file:///tmp/cmr-es-spatial-plugin-0.1.0-SNAPSHOT.zip --batch &&\
    chown -R elasticsearch:elasticsearch /usr/share/elasticsearch/plugins &&\
    echo "root soft memlock unlimited" >> /etc/security/limits.conf &&\
    echo "root hard memlock unlimited" >> /etc/security/limits.conf

WORKDIR /

USER 1000
ENV HOME=/usr/share/elasticsearch
