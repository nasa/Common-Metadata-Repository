FROM clojure:temurin-17-lein-trixie

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive \
    apt-get install --assume-yes \
      python3 \
      curl \
      netbase \
      unzip \
      zip \
 && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /root/.lein
COPY profiles.bamboo.clj /root/.lein/profiles.clj

# Downgrade Leiningen to 2.10.0 to fix compatibility issues with lein-modules 0.3.11
RUN curl -L https://raw.githubusercontent.com/technomancy/leiningen/2.10.0/bin/lein -o /usr/local/bin/lein \
 && chmod a+x /usr/local/bin/lein \
 && lein version
