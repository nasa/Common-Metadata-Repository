FROM centos:7

# Install Java and clean up.
COPY jdk-8u161-linux-x64.rpm /tmp
RUN yum localinstall -y /tmp/jdk-8u161-linux-x64.rpm && \
	rm /tmp/jdk-8u161-linux-x64.rpm && \
	rm -rf /var/cache/yum
ENV JAVA_HOME /usr/java/jdk1.8.0_161/jre
