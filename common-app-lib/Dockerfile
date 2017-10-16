FROM centos:7

# Install Java and clean up.
RUN curl -LO --cookie 'oraclelicense=accept-securebackup-cookie' 'http://download.oracle.com/otn-pub/java/jdk/8u144-b01/090f390dda5b47b9b721c7dfaa008135/jdk-8u144-linux-x64.rpm' \
&& rpm -Uvh jdk-8u144-linux-x64.rpm \
&& rm jdk-8u144-linux-x64.rpm
ENV JAVA_HOME /usr/java/jdk1.8.0_144/jre

