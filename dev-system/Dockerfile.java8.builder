FROM centos:centos7

EXPOSE 2999-3012

# Workaround for http://bugs.centos.org/view.php?id=7126
RUN /bin/sed -i '/\[centosplus\]/,/enabled/ s/^enabled.*/enabled=1/' /etc/yum.repos.d/CentOS-Base.repo

# Install Java and clean up.
RUN curl -LO 'https://maven.earthdata.nasa.gov/repository/heg-c/tools/jdk-8u211-linux-x64.rpm' \
 && rpm -Uvh jdk-8u211-linux-x64.rpm \
 && rm jdk-8u211-linux-x64.rpm
ENV JAVA_HOME /usr/java/jdk1.8.0_211/jre

# Install Maven
RUN yum install -y bash gcc git tar which unzip zip docker openssl \
 && yum clean all
WORKDIR /opt
RUN curl -LO https://dlcdn.apache.org/maven/maven-3/3.8.8/binaries/apache-maven-3.8.8-bin.tar.gz \
 && tar -xzf apache-maven-3.8.8-bin.tar.gz \
 && rm apache-maven-3.8.8-bin.tar.gz
ENV PATH /opt/apache-maven-3.8.8/bin:${PATH}

# Create bamboo user
RUN groupadd -g 500 bamboo
RUN groupadd  docker
RUN useradd --gid bamboo --create-home --uid 500 bamboo
RUN usermod -a -G docker bamboo
#ENV HOME /home/bamboo
ENV PATH ${PATH}:/build/bin

# Install Leiningen
WORKDIR /usr/local/bin
RUN curl -LO https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
RUN chmod 0555 lein
ENV HTTP_CLIENT curl --insecure -f -L -o
USER bamboo
RUN lein --version

# Default run options
USER bamboo
RUN cd $HOME/.lein && curl -LO https://maven.earthdata.nasa.gov/repository/cmr/profiles.clj
WORKDIR /build
